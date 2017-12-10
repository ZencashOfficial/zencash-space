/************************************************************************************************
 *  _________          _     ____          _           __        __    _ _      _   _   _ ___
 * |__  / ___|__ _ ___| |__ / ___|_      _(_)_ __   __ \ \      / /_ _| | | ___| |_| | | |_ _|
 *   / / |   / _` / __| '_ \\___ \ \ /\ / / | '_ \ / _` \ \ /\ / / _` | | |/ _ \ __| | | || |
 *  / /| |__| (_| \__ \ | | |___) \ V  V /| | | | | (_| |\ V  V / (_| | | |  __/ |_| |_| || |
 * /____\____\__,_|___/_| |_|____/ \_/\_/ |_|_| |_|\__, | \_/\_/ \__,_|_|_|\___|\__|\___/|___|
 *                                                 |___/
 *
 * Copyright (c) 2016 Ivan Vaklinov <ivan@vaklinov.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 **********************************************************************************/
package com.vaklinov.zcashui;


import static net.ddns.lsmobile.zencashvaadinwalletui4cpp.business.IConfig.log;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;

import com.vaklinov.zcashui.OSUtil.OS_TYPE;
import com.vaklinov.zcashui.ZCashClientCaller.WalletCallException;

import net.ddns.lsmobile.zencashvaadinwalletui4cpp.business.IConfig;


/**
 * Addresses panel - shows T/Z addresses and their balnces.
 *
 * @author Ivan Vaklinov <ivan@vaklinov.com>
 */
public class AddressesPanel
	extends WalletTabPanel implements IConfig
{
	private ZCashClientCaller clientCaller;
	private StatusUpdateErrorReporter errorReporter;

	private JTable addressBalanceTable   = null;
	private JScrollPane addressBalanceTablePane  = null;
	
	String[][] lastAddressBalanceData = null;
	
	private DataGatheringThread<String[][]> balanceGatheringThread = null;
	
	private long lastInteractiveRefresh;
	

	public AddressesPanel(final ZCashClientCaller clientCaller, final StatusUpdateErrorReporter errorReporter)
		throws IOException, InterruptedException, WalletCallException
	{
		this.clientCaller = clientCaller;
		this.errorReporter = errorReporter;
		
		this.lastInteractiveRefresh = System.currentTimeMillis();

		// Build content
		final JPanel addressesPanel = this;
		addressesPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		addressesPanel.setLayout(new BorderLayout(0, 0));
	
		// Build panel of buttons
		final JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
		buttonPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		
		final JButton newTAddressButton = new JButton("New T (Transparent) address");
		buttonPanel.add(newTAddressButton);
		final JButton newZAddressButton = new JButton("New Z (Private) address");
		buttonPanel.add(newZAddressButton);
		buttonPanel.add(new JLabel("           "));
		final JButton refreshButton = new JButton("Refresh");
		buttonPanel.add(refreshButton);
		
		addressesPanel.add(buttonPanel, BorderLayout.SOUTH);

		// Table of transactions
		this.lastAddressBalanceData = getAddressBalanceDataFromWallet();
		addressesPanel.add(this.addressBalanceTablePane = new JScrollPane(
				               this.addressBalanceTable = this.createAddressBalanceTable(this.lastAddressBalanceData)),
				           BorderLayout.CENTER);
		
		
		final JPanel warningPanel = new JPanel();
		warningPanel.setLayout(new BorderLayout(3, 3));
		warningPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		final JLabel warningL = new JLabel(
				"<html><span style=\"font-size:0.8em;\">" +
				"* If the balance of an address is flagged as not confirmed, the address is currently taking " +
				"part in a transaction. The shown balance then is the expected value it will have when " +
				"the transaction is confirmed. " +
				"The average confirmation time is 2.5 min." +
			    "</span>");
		warningPanel.add(warningL, BorderLayout.NORTH);
		addressesPanel.add(warningPanel, BorderLayout.NORTH);
		
		// Thread and timer to update the address/balance table
		this.balanceGatheringThread = new DataGatheringThread<>(
			new DataGatheringThread.DataGatherer<String[][]>()
			{
				@Override
				public String[][] gatherData()
					throws Exception
				{
					final long start = System.currentTimeMillis();
					final String[][] data = AddressesPanel.this.getAddressBalanceDataFromWallet();
					final long end = System.currentTimeMillis();
					log.info("Gathering of address/balance table data done in " + (end - start) + "ms." );
					
				    return data;
				}
			},
			this.errorReporter, 25000);
		this.threads.add(this.balanceGatheringThread);
		
		final ActionListener alBalances = new ActionListener()
		{
			@Override
			public void actionPerformed(final ActionEvent e)
			{
				try
				{
					AddressesPanel.this.updateWalletAddressBalanceTableAutomated();
				} catch (final Exception ex)
				{
					log.error("Unexpected error: ", ex);
					AddressesPanel.this.errorReporter.reportError(ex);
				}
			}
		};
		final Timer t = new Timer(5000, alBalances);
		t.start();
		this.timers.add(t);
		
		// Button actions
		refreshButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(final ActionEvent e)
			{
				Cursor oldCursor = null;
				try
				{
					// TODO: dummy progress bar ... maybe
					oldCursor = AddressesPanel.this.getCursor();
					AddressesPanel.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					
					AddressesPanel.this.updateWalletAddressBalanceTableInteractive();
					
					AddressesPanel.this.setCursor(oldCursor);
				} catch (final Exception ex)
				{
					if (oldCursor != null)
					{
						AddressesPanel.this.setCursor(oldCursor);
					}
					
					log.error("Unexpected error: ", ex);
					AddressesPanel.this.errorReporter.reportError(ex, false);
				}
			}
		});
		
		newTAddressButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(final ActionEvent e)
			{
				createNewAddress(false);
			}
		});
		
		newZAddressButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(final ActionEvent e)
			{
				createNewAddress(true);
			}
		});
		
	}
	
	
	// Null if not selected
	public String getSelectedAddress()
	{
		String address = null;
		
		final int selectedRow = this.addressBalanceTable.getSelectedRow();
		
		if (selectedRow != -1)
		{
			address = this.addressBalanceTable.getModel().getValueAt(selectedRow, 2).toString();
		}
		
		return address;
	}

	
	private void createNewAddress(final boolean isZAddress)
	{
		try
		{
			// Check for encrypted wallet
			final boolean bEncryptedWallet = this.clientCaller.isWalletEncrypted();
			if (bEncryptedWallet && isZAddress)
			{
				final PasswordDialog pd = new PasswordDialog((JFrame)(this.getRootPane().getParent()));
				pd.setVisible(true);
				
				if (!pd.isOKPressed())
				{
					return;
				}
				
				this.clientCaller.unlockWallet(pd.getPassword());
			}

			final String address = this.clientCaller.createNewAddress(isZAddress);
			
			// Lock the wallet again
			if (bEncryptedWallet && isZAddress)
			{
				this.clientCaller.lockWallet();
			}
						
			JOptionPane.showMessageDialog(
				this.getRootPane().getParent(),
				"A new " + (isZAddress ? "Z (Private)" : "T (Transparent)")
				+ " address has been created cuccessfully:\n" + address,
				"Address created", JOptionPane.INFORMATION_MESSAGE);
			
			this.updateWalletAddressBalanceTableInteractive();
		} catch (final Exception e)
		{
			log.error("Unexpected error: ", e);
			AddressesPanel.this.errorReporter.reportError(e, false);
		}
	}
	
	// Interactive and non-interactive are mutually exclusive
	private synchronized void updateWalletAddressBalanceTableInteractive()
		throws WalletCallException, IOException, InterruptedException
	{
		this.lastInteractiveRefresh = System.currentTimeMillis();
		
		final String[][] newAddressBalanceData = this.getAddressBalanceDataFromWallet();

		if (Util.arraysAreDifferent(this.lastAddressBalanceData, newAddressBalanceData))
		{
			log.info("Updating table of addresses/balances I...");
			this.remove(this.addressBalanceTablePane);
			this.add(this.addressBalanceTablePane = new JScrollPane(
			             this.addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)),
			         BorderLayout.CENTER);
			this.lastAddressBalanceData = newAddressBalanceData;

			this.validate();
			this.repaint();
		}
	}
	
	
	// Interactive and non-interactive are mutually exclusive
	private synchronized void updateWalletAddressBalanceTableAutomated()
		throws WalletCallException, IOException, InterruptedException
	{
		// Make sure it is > 1 min since the last interactive refresh
		if ((System.currentTimeMillis() - this.lastInteractiveRefresh) < (60 * 1000))
		{
			return;
		}
		
		final String[][] newAddressBalanceData = this.balanceGatheringThread.getLastData();
		
		if ((newAddressBalanceData != null) &&
			Util.arraysAreDifferent(this.lastAddressBalanceData, newAddressBalanceData))
		{
			log.info("Updating table of addresses/balances A...");
			this.remove(this.addressBalanceTablePane);
			this.add(this.addressBalanceTablePane = new JScrollPane(
			             this.addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)),
		         BorderLayout.CENTER);
			this.lastAddressBalanceData = newAddressBalanceData;
			this.validate();
			this.repaint();
		}
	}


	private JTable createAddressBalanceTable(final String rowData[][])
		throws WalletCallException, IOException, InterruptedException
	{
		final String columnNames[] = { "Balance", "Confirmed?", "Address" };
        final JTable table = new AddressTable(rowData, columnNames, this.clientCaller);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(1000);

        return table;
	}


	private String[][] getAddressBalanceDataFromWallet()
		throws WalletCallException, IOException, InterruptedException
	{
		// Z Addresses - they are OK
		final String[] zAddresses = this.clientCaller.getWalletZAddresses();
		
		// T Addresses listed with the list received by addr comamnd
		final String[] tAddresses = this.clientCaller.getWalletAllPublicAddresses();
		final Set<String> tStoredAddressSet = new HashSet<>();
		for (final String address : tAddresses)
		{
			tStoredAddressSet.add(address);
		}
		
		// T addresses with unspent outputs - just in case they are different
		final String[] tAddressesWithUnspentOuts = this.clientCaller.getWalletPublicAddressesWithUnspentOutputs();
		final Set<String> tAddressSetWithUnspentOuts = new HashSet<>();
		for (final String address : tAddressesWithUnspentOuts)
		{
			tAddressSetWithUnspentOuts.add(address);
		}
		
		// Combine all known T addresses
		final Set<String> tAddressesCombined = new HashSet<>();
		tAddressesCombined.addAll(tStoredAddressSet);
		tAddressesCombined.addAll(tAddressSetWithUnspentOuts);
		
		final String[][] addressBalances = new String[zAddresses.length + tAddressesCombined.size()][];
		
		// Format double numbers - else sometimes we get exponential notation 1E-4 ZEN
		final DecimalFormat df = new DecimalFormat("########0.00######");
		
		String confirmed    = "\u2690";
		String notConfirmed = "\u2691";
		
		// Windows does not support the flag symbol (Windows 7 by default)
		// TODO: isolate OS-specific symbol codes in a separate class
		final OS_TYPE os = OSUtil.getOSType();
		if (os == OS_TYPE.WINDOWS)
		{
			confirmed = " \u25B7";
			notConfirmed = " \u25B6";
		}
		
		int i = 0;

		for (final String address : tAddressesCombined)
		{
			final String confirmedBalance = this.clientCaller.getBalanceForAddress(address);
			final String unconfirmedBalance = this.clientCaller.getUnconfirmedBalanceForAddress(address);
			final boolean isConfirmed =  (confirmedBalance.equals(unconfirmedBalance));
			final String balanceToShow = df.format(Double.valueOf(
				isConfirmed ? confirmedBalance : unconfirmedBalance));
			
			addressBalances[i++] = new String[]
			{
				balanceToShow,
				isConfirmed ? ("Yes " + confirmed) : ("No  " + notConfirmed),
				address
			};
		}
		
		for (final String address : zAddresses)
		{
			final String confirmedBalance = this.clientCaller.getBalanceForAddress(address);
			final String unconfirmedBalance = this.clientCaller.getUnconfirmedBalanceForAddress(address);
			final boolean isConfirmed =  (confirmedBalance.equals(unconfirmedBalance));
			final String balanceToShow = df.format(Double.valueOf(
				isConfirmed ? confirmedBalance : unconfirmedBalance));
			
			addressBalances[i++] = new String[]
			{
				balanceToShow,
				isConfirmed ? ("Yes " + confirmed) : ("No  " + notConfirmed),
				address
			};
		}

		return addressBalances;
	}

}
