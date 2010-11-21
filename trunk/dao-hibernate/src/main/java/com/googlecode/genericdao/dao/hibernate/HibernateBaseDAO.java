/* Copyright 2009 The Revere Group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.genericdao.dao.hibernate;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Criteria;
import org.hibernate.NonUniqueResultException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import com.googlecode.genericdao.search.ExampleOptions;
import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.SearchResult;
import com.googlecode.genericdao.search.hibernate.HibernateMetadataUtil;
import com.googlecode.genericdao.search.hibernate.HibernateSearchProcessor;

/**
 * Base class for DAOs that uses Hibernate SessionFactory and HQL for searches.
 * This is the heart of Hibernate Generic DAO.
 * 
 * @author dwolverton
 * 
 */
@SuppressWarnings("unchecked")
public class HibernateBaseDAO {

	private HibernateSearchProcessor searchProcessor;

	private SessionFactory sessionFactory;

	private HibernateMetadataUtil metaDataUtil;

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
		searchProcessor = HibernateSearchProcessor.getInstanceForSessionFactory(sessionFactory);
		metaDataUtil = HibernateMetadataUtil.getInstanceForSessionFactory(sessionFactory);
	}

	protected SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	/**
	 * Get the current Hibernate session
	 */
	protected Session getSession() {
		return sessionFactory.getCurrentSession();
	}

	/**
	 * Get the instance of HibernateMetaDataUtil associated with the session
	 * factory
	 */
	protected HibernateMetadataUtil getMetaDataUtil() {
		return metaDataUtil;
	}

	/**
	 * Get the instance of EJBSearchProcessor associated with the session
	 * factory
	 */
	protected HibernateSearchProcessor getSearchProcessor() {
		return searchProcessor;
	}

	/**
	 * <p>
	 * Persist the given transient instance and add it to the datastore, first
	 * assigning a generated identifier. (Or using the current value of the
	 * identifier property if the assigned generator is used.) This operation
	 * cascades to associated instances if the association is mapped with
	 * cascade="save-update".
	 * 
	 * <p>
	 * This is different from <code>persist()</code> in that it does guarantee
	 * that the object will be assigned an identifier immediately. With
	 * <code>save()</code> a call is made to the datastore immediately if the id
	 * is generated by the datastore so that the id can be determined. With
	 * <code>persist</code> this call may not occur until flush time.
	 * 
	 * @return The id of the newly saved entity.
	 */
	protected Serializable _save(Object entity) {
		return getSession().save(entity);
	}

	/**
	 * Persist the given transient instances and add them to the datastore,
	 * first assigning a generated identifier. (Or using the current value of
	 * the identifier property if the assigned generator is used.) This
	 * operation cascades to associated instances if the association is mapped
	 * with cascade="save-update".
	 */
	protected void _save(Object... entities) {
		for (Object entity : entities) {
			_save(entity);
		}
	}

	/**
	 * <p>
	 * Calls Hibernate's <code>saveOrUpdate()</code>, which behaves as follows:
	 * 
	 * <p>
	 * Either <code>save()</code> or <code>update()</code> based on the
	 * following rules
	 * <ul>
	 * <li>if the object is already persistent in this session, do nothing
	 * <li>
	 * if another object associated with the session has the same identifier,
	 * throw an exception
	 * <li>if the object has no identifier property, save() it
	 * <li>if the object's identifier has the value assigned to a newly
	 * instantiated object, save() it
	 * <li>if the object is versioned (by a &lt;version&gt; or
	 * &lt;timestamp&gt;), and the version property value is the same value
	 * assigned to a newly instantiated object, save() it
	 * <li>otherwise update() the object
	 * </ul>
	 */
	protected void _saveOrUpdate(Object entity) {
		getSession().saveOrUpdate(entity);
	}

	/**
	 * <p>
	 * If an entity already exists in the datastore with the same id, call
	 * _update and return false (not new). If no such entity exists in the
	 * datastore, call _save() and return true (new)
	 * 
	 * @return <code>true</code> if _save(); <code>false</code> if _update().
	 */
	protected boolean _saveOrUpdateIsNew(Object entity) {
		if (entity == null)
			throw new IllegalArgumentException("attempt to saveOrUpdate with null entity");

		Serializable id = getMetaDataUtil().getId(entity);
		if (getSession().contains(entity))
			return false;

		if (id == null || (new Long(0)).equals(id) || !_exists(entity)) {
			_save(entity);
			return true;
		} else {
			_update(entity);
			return false;
		}
	}

	/**
	 * Either <code>save()</code> or <code>update()</code> each entity,
	 * depending on whether or not an entity with the same id already exists in
	 * the datastore.
	 * 
	 * @return an boolean array corresponding to to the input list of entities.
	 *         Each element is <code>true</code> if the corresponding entity was
	 *         <code>_save()</code>d or <code>false</code> if it was
	 *         <code>_update()</code>d.
	 */
	protected boolean[] _saveOrUpdateIsNew(Object... entities) {
		Boolean[] exists = new Boolean[entities.length];

		// if an entity is contained in the session, it exists; if it has no id,
		// it does not exist
		for (int i = 0; i < entities.length; i++) {
			if (entities[i] == null) {
				throw new IllegalArgumentException("attempt to saveOrUpdate with null entity");
			}
			if (getSession().contains(entities[i])) {
				exists[i] = true;
			} else {
				Serializable id = getMetaDataUtil().getId(entities[i]);
				if (id == null || (new Long(0)).equals(id)) {
					exists[i] = false;
				}
			}
		}

		// if it has an id and is not contained in the session, it may exist
		Map<Class<?>, List<Integer>> mayExist = new HashMap<Class<?>, List<Integer>>();
		for (int i = 0; i < entities.length; i++) {
			if (exists[i] == null) {
				Class<?> entityClass = metaDataUtil.getUnproxiedClass(entities[i]); //Get the real entity class
				List<Integer> l = mayExist.get(entityClass);
				if (l == null) {
					l = new ArrayList<Integer>();
					mayExist.put(entityClass, l);
				}
				l.add(i);
			}
		}

		// for each type of entity, do a batch call to the datastore to see
		// which of the entities of that class exist
		for (Map.Entry<Class<?>, List<Integer>> entry : mayExist.entrySet()) {
			Serializable[] ids = new Serializable[entry.getValue().size()];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = getMetaDataUtil().getId(entities[entry.getValue().get(i)]);
			}
			boolean exists2[] = _exists(entry.getKey(), ids);
			for (int i = 0; i < ids.length; i++) {
				exists[entry.getValue().get(i)] = exists2[i];
			}
		}

		boolean[] isNew = new boolean[entities.length];
		// now that we know which ones exist, save or update each.
		for (int i = 0; i < entities.length; i++) {
			if (entities[i] != null) {
				if (exists[i]) {
					_update(entities[i]);
					isNew[i] = false;
				} else {
					_save(entities[i]);
					isNew[i] = true;
				}
			}
		}

		return isNew;
	}

	/**
	 * <p>
	 * Make a transient instance persistent and add it to the datastore. This
	 * operation cascades to associated instances if the association is mapped
	 * with cascade="persist". Throws an error if the entity already exists.
	 * 
	 * <p>
	 * This is different from <code>save()</code> in that it does not guarantee
	 * that the object will be assigned an identifier immediately. With
	 * <code>save()</code> a call is made to the datastore immediately if the id
	 * is generated by the datastore so that the id can be determined. With
	 * <code>persist</code> this call may not occur until flush time.
	 */
	protected void _persist(Object... entities) {
		for (Object entity : entities) {
			getSession().persist(entity);
		}
	}

	/**
	 * Remove the entity of the specified class with the specified id from the
	 * datastore.
	 * 
	 * @return <code>true</code> if the object is found in the datastore and
	 *         deleted, <code>false</code> if the item is not found.
	 */
	protected boolean _deleteById(Class<?> type, Serializable id) {
		if (id != null) {
			type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
			Object entity = getSession().get(type, id);
			if (entity != null) {
				getSession().delete(entity);
				return true;
			}
		}
		return false;
	}

	/**
	 * Remove all the entities of the given type from the datastore that have
	 * one of these ids.
	 */
	protected void _deleteById(Class<?> type, Serializable... ids) {
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
		Criteria c = getSession().createCriteria(type);
		c.add(Restrictions.in("id", ids));
		for (Object entity : c.list()) {
			getSession().delete(entity);
		}
	}

	/**
	 * Remove the specified entity from the datastore.
	 * 
	 * @return <code>true</code> if the object is found in the datastore and
	 *         removed, <code>false</code> if the item is not found.
	 */
	protected boolean _deleteEntity(Object entity) {
		if (entity != null) {
			Serializable id = getMetaDataUtil().getId(entity);
			if (id != null) {
				entity = getSession().get(metaDataUtil.getUnproxiedClass(entity), id);
				if (entity != null) {
					getSession().delete(entity);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Remove the specified entities from the datastore.
	 */
	protected void _deleteEntities(Object... entities) {
		for (Object entity : entities) {
			if (entity != null)
				getSession().delete(entity);
		}
	}

	/**
	 * Return the persistent instance of the given entity class with the given
	 * identifier, or null if there is no such persistent instance.
	 * <code>get()</code> always hits the database immediately.
	 */
	protected <T> T _get(Class<T> type, Serializable id) {
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
		return (T) getSession().get(type, id);
	}

	/**
	 * <p>
	 * Return the all the persistent instances of the given entity class with
	 * the given identifiers. An array of entities is returned that matches the
	 * same order of the ids listed in the call. For each entity that is not
	 * found in the datastore, a null will be inserted in its place in the
	 * return array.
	 * 
	 * <p>
	 * <code>get()</code> always hits the database immediately.
	 */
	protected <T> T[] _get(Class<T> type, Serializable... ids) {
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
		Criteria c = getSession().createCriteria(type);
		c.add(Restrictions.in("id", ids));
		Object[] retVal = (Object[]) Array.newInstance(type, ids.length);

		for (Object entity : c.list()) {
			Serializable id = getMetaDataUtil().getId(entity);
			for (int i = 0; i < ids.length; i++) {
				if (id.equals(ids[i])) {
					retVal[i] = entity;
					break;
				}
			}
		}

		return (T[]) retVal;
	}

	/**
	 * <p>
	 * Return the persistent instance of the given entity class with the given
	 * identifier, assuming that the instance exists. Throw an unrecoverable
	 * exception if there is no matching database row.
	 * 
	 * <p>
	 * If the class is mapped with a proxy, <code>load()</code> just returns an
	 * uninitialized proxy and does not actually hit the database until you
	 * invoke a method of the proxy. This behaviour is very useful if you wish
	 * to create an association to an object without actually loading it from
	 * the database. It also allows multiple instances to be loaded as a batch
	 * if batch-size is defined for the class mapping.
	 */
	protected <T> T _load(Class<T> type, Serializable id) {
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
		return (T) getSession().load(type, id);
	}

	/**
	 * <p>
	 * Return the persistent instance of the given entity class with the given
	 * identifier, assuming that the instance exists. Throw an unrecoverable
	 * exception if there is no matching database row. An array of entities is
	 * returned that matches the same order of the ids listed in the call. For
	 * each entity that is not found in the datastore, a null will be inserted
	 * in its place in the return array.
	 * 
	 * @see #_load(Class, Serializable)
	 */
	protected <T> T[] _load(Class<T> type, Serializable... ids) {
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
		Object[] retVal = (Object[]) Array.newInstance(type, ids.length);
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] != null)
				retVal[i] = _load(type, ids[i]);
		}
		return (T[]) retVal;
	}

	/**
	 * Read the persistent state associated with the given identifier into the
	 * given transient instance. Throw an unrecoverable exception if there is no
	 * matching database row.
	 */
	protected void _load(Object transientEntity, Serializable id) {
		getSession().load(transientEntity, id);
	}

	/**
	 * Get a list of all the objects of the specified class.
	 */
	protected <T> List<T> _all(Class<T> type) {
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class
		return getSession().createCriteria(type).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list();
	}

	/**
	 * <p>
	 * Update the persistent instance with the identifier of the given detached
	 * instance. If there is a persistent instance with the same identifier, an
	 * exception is thrown. This operation cascades to associated instances if
	 * the association is mapped with cascade="save-update".
	 * 
	 * <p>
	 * The difference between <code>update()</code> and <code>merge()</code> is
	 * significant: <code>update()</code> will make the given object persistent
	 * and throw and error if another object with the same ID is already
	 * persistent in the Session. <code>merge()</code> doesn't care if another
	 * object is already persistent, but it also doesn't make the given object
	 * persistent; it just copies over the values to the datastore.
	 */
	protected void _update(Object... transientEntities) {
		for (Object entity : transientEntities) {
			getSession().update(entity);
		}
	}

	/**
	 * <p>
	 * Copy the state of the given object onto the persistent object with the
	 * same identifier. If there is no persistent instance currently associated
	 * with the session, it will be loaded. Return the persistent instance. If
	 * the given instance is unsaved, save a copy of and return it as a newly
	 * persistent instance. The given instance does not become associated with
	 * the session. This operation cascades to associated instances if the
	 * association is mapped with cascade="merge".
	 * 
	 * <p>
	 * The difference between <code>update()</code> and <code>merge()</code> is
	 * significant: <code>update()</code> will make the given object persistent
	 * and throw and error if another object with the same ID is already
	 * persistent in the Session. <code>merge()</code> doesn't care if another
	 * object is already persistent, but it also doesn't make the given object
	 * persistent; it just copies over the values to the datastore.
	 */
	protected <T> T _merge(T entity) {
		return (T) getSession().merge(entity);
	}

	/**
	 * Search for objects based on the search parameters in the specified
	 * <code>ISearch</code> object.
	 * 
	 * @see ISearch
	 */
	protected List _search(ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (search.getSearchClass() == null)
			throw new NullPointerException("Search class is null.");

		return getSearchProcessor().search(getSession(), search);
	}

	/**
	 * Same as <code>_search(ISearch)</code> except that it uses the specified
	 * search class instead of getting it from the search object. Also, if the search
	 * object has a different search class than what is specified, an exception
	 * is thrown.
	 */
	protected List _search(Class<?> searchClass, ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (searchClass == null)
			throw new NullPointerException("Search class is null.");
		if (search.getSearchClass() != null && !search.getSearchClass().equals(searchClass))
			throw new IllegalArgumentException("Search class does not match expected type: " + searchClass.getName());

		return getSearchProcessor().search(getSession(), searchClass, search);
	}

	/**
	 * Returns the total number of results that would be returned using the
	 * given <code>ISearch</code> if there were no paging or maxResult limits.
	 * 
	 * @see ISearch
	 */
	protected int _count(ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (search.getSearchClass() == null)
			throw new NullPointerException("Search class is null.");

		return getSearchProcessor().count(getSession(), search);
	}

	/**
	 * Same as <code>_count(ISearch)</code> except that it uses the specified
	 * search class instead of getting it from the search object. Also, if the search
	 * object has a different search class than what is specified, an exception
	 * is thrown.
	 */
	protected int _count(Class<?> searchClass, ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (searchClass == null)
			throw new NullPointerException("Search class is null.");
		if (search.getSearchClass() != null && !search.getSearchClass().equals(searchClass))
			throw new IllegalArgumentException("Search class does not match expected type: " + searchClass.getName());

		return getSearchProcessor().count(getSession(), searchClass, search);
	}

	/**
	 * Returns the number of instances of this class in the datastore.
	 */
	protected int _count(Class<?> type) {
		List counts = getSession().createQuery("select count(_it_) from " + type.getName() + " _it_").list();
		int sum = 0;
		for (Object count : counts) {
			sum += ((Long) count).intValue();
		}
		return sum;
	}

	/**
	 * Returns a <code>SearchResult</code> object that includes the list of
	 * results like <code>search()</code> and the total length like
	 * <code>searchLength</code>.
	 * 
	 * @see ISearch
	 */
	protected SearchResult _searchAndCount(ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (search.getSearchClass() == null)
			throw new NullPointerException("Search class is null.");

		return getSearchProcessor().searchAndCount(getSession(), search);
	}

	/**
	 * Same as <code>_searchAndCount(ISearch)</code> except that it uses the specified
	 * search class instead of getting it from the search object. Also, if the search
	 * object has a different search class than what is specified, an exception
	 * is thrown.
	 */
	protected SearchResult _searchAndCount(Class<?> searchClass, ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (searchClass == null)
			throw new NullPointerException("Search class is null.");
		if (search.getSearchClass() != null && !search.getSearchClass().equals(searchClass))
			throw new IllegalArgumentException("Search class does not match expected type: " + searchClass.getName());

		return getSearchProcessor().searchAndCount(getSession(), searchClass, search);
	}

	/**
	 * Search for a single result using the given parameters.
	 */
	protected Object _searchUnique(ISearch search) throws NonUniqueResultException {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (search.getSearchClass() == null)
			throw new NullPointerException("Search class is null.");

		return getSearchProcessor().searchUnique(getSession(), search);
	}

	/**
	 * Same as <code>_searchUnique(ISearch)</code> except that it uses the specified
	 * search class instead of getting it from the search object. Also, if the search
	 * object has a different search class than what is specified, an exception
	 * is thrown.
	 */
	protected Object _searchUnique(Class<?> searchClass, ISearch search) {
		if (search == null)
			throw new NullPointerException("Search is null.");
		if (searchClass == null)
			throw new NullPointerException("Search class is null.");
		if (search.getSearchClass() != null && !search.getSearchClass().equals(searchClass))
			throw new IllegalArgumentException("Search class does not match expected type: " + searchClass.getName());

		return getSearchProcessor().searchUnique(getSession(), searchClass, search);
	}

	/**
	 * Returns true if the object is connected to the current hibernate session.
	 */
	protected boolean _sessionContains(Object o) {
		return getSession().contains(o);
	}

	/**
	 * Flushes changes in the hibernate cache to the datastore.
	 */
	protected void _flush() {
		getSession().flush();
	}

	/**
	 * Refresh the content of the given entity from the current datastore state.
	 */
	protected void _refresh(Object... entities) {
		for (Object entity : entities)
			getSession().refresh(entity);
	}

	protected boolean _exists(Object entity) {
		if (getSession().contains(entity))
			return true;
		return _exists(entity.getClass(), getMetaDataUtil().getId(entity));
	}

	protected boolean _exists(Class<?> type, Serializable id) {
		if (type == null)
			throw new NullPointerException("Type is null.");
		if (id == null)
			return false;
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class

		Query query = getSession().createQuery("select id from " + type.getName() + " where id = :id");
		query.setParameter("id", id);
		return query.list().size() == 1;
	}

	protected boolean[] _exists(Class<?> type, Serializable... ids) {
		if (type == null)
			throw new NullPointerException("Type is null.");
		type = metaDataUtil.getUnproxiedClass(type); //Get the real entity class

		boolean[] ret = new boolean[ids.length];

		// we can't use "id in (:ids)" because some databases do not support
		// this for compound ids.
		StringBuilder sb = new StringBuilder("select id from " + type.getName() + " where");
		boolean first = true;
		for (int i = 0; i < ids.length; i++) {
			if (first) {
				first = false;
				sb.append(" id = :id");
			} else {
				sb.append(" or id = :id");
			}
			sb.append(i);
		}

		Query query = getSession().createQuery(sb.toString());
		for (int i = 0; i < ids.length; i++) {
			query.setParameter("id" + i, ids[i]);
		}

		for (Serializable id : (List<Serializable>) query.list()) {
			for (int i = 0; i < ids.length; i++) {
				if (id.equals(ids[i])) {
					ret[i] = true;
					// don't break. the same id could be in the list twice.
				}
			}
		}

		return ret;
	}
	
	protected Filter _getFilterFromExample(Object example) {
		return searchProcessor.getFilterFromExample(example);
	}
	
	protected Filter _getFilterFromExample(Object example, ExampleOptions options) {
		return searchProcessor.getFilterFromExample(example, options);
	}
}
