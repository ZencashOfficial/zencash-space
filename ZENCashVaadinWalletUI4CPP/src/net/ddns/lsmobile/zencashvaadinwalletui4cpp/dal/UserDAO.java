
package net.ddns.lsmobile.zencashvaadinwalletui4cpp.dal;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;

import com.xdev.dal.JPADAO;

import net.ddns.lsmobile.zencashvaadinwalletui4cpp.entities.User;
import net.ddns.lsmobile.zencashvaadinwalletui4cpp.entities.User_;

/**
 * Home object for domain model class User.
 * 
 * @see User
 */
public class UserDAO extends JPADAO<User, Integer> {
	public UserDAO() {
		super(User.class);
	}

	/**
	 * @queryMethod Do not edit, method is generated by editor!
	 */
	public List<User> findUsers(final String username) {
		final EntityManager entityManager = em();
	
		final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
	
		final ParameterExpression<String> usernameParameter = criteriaBuilder.parameter(String.class, "username");
	
		final CriteriaQuery<User> criteriaQuery = criteriaBuilder.createQuery(User.class);
	
		final Root<User> root = criteriaQuery.from(User.class);
	
		criteriaQuery.where(criteriaBuilder.like(root.get(User_.username), usernameParameter));
	
		final TypedQuery<User> query = entityManager.createQuery(criteriaQuery);
		query.setParameter(usernameParameter, username);
		return query.getResultList();
	}
}