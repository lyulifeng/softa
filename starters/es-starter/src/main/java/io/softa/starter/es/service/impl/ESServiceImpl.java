package io.softa.starter.es.service.impl;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;

import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.StringTools;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.FilterUnit;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.orm.enums.FilterType;
import io.softa.framework.orm.enums.LogicOperator;
import io.softa.starter.es.service.ESService;

/**
 * Implementation of Common ES service
 * @param <T> entity type stored in ES
 */
public abstract class ESServiceImpl<T> implements ESService<T> {

    private Class<T> entityClass;

    @Autowired
    protected ElasticsearchOperations esOperations;

    /** Get entity class **/
    @SuppressWarnings("unchecked")
    private Class<T> getEntityClass() {
        if (entityClass == null) {
            entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        }
        return entityClass;
    }

    protected ESServiceImpl() {
        this.entityClass = getEntityClass();
    }

    /**
     * ES service implementation class must specify the index name by implementing this method
     *
     * @return index name
     */
    public abstract String getIndexName();

    /**
     * ES paging query object data
     *
     * @param filters   filter conditions
     * @param orders    sort rules
     * @param page      paging object
     * @return a page of indexed data
     */
    @Override
    public Page<T> searchPage(Filters filters, Orders orders, Page<T> page) {
        return searchPage(getEntityClass(), filters, orders, page);
    }

    /**
     * Execute the paged ES query against an arbitrary target document class.
     * Allows subclasses to query a storage-shaped DTO (e.g. with JSON-string
     * payload fields) and map back to the public entity afterwards.
     *
     * @param targetClass document class to deserialize hits into
     * @param filters     filter conditions
     * @param orders      sort rules
     * @param page        paging object — populated with totalCount and rows
     * @param <U>         target document type
     * @return the same {@code page}, with hits and total count filled in
     */
    @Override
    public <U> Page<U> searchPage(Class<U> targetClass, Filters filters, Orders orders, Page<U> page) {
        Criteria criteria = this.convertFilters(filters);
        Query query = new CriteriaQuery(criteria);
        // ES offset is started from 0, so the calculation is '(page - 1) * pageSize'
        Pageable pageable = PageRequest.of(page.getPageNumber() - 1, page.getPageSize());
        query.setPageable(pageable);
        // Sort by multiple fields
        if (orders != null) {
            orders.getOrderList().forEach(order -> {
                String field = order.get(0);
                String direction = order.get(1);
                Sort.Direction sortDirection = Orders.DESC.equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
                query.addSort(Sort.by(sortDirection, field));
            });
        }
        // Set trackTotalHits
        query.setTrackTotalHits(true);
        // Execute query
        SearchHits<U> searchHits = esOperations.search(query, targetClass, IndexCoordinates.of(getIndexName()));
        List<U> entityList = searchHits.getSearchHits().stream().map(SearchHit::getContent).toList();
        page.setTotalCount(searchHits.getTotalHits());
        page.setRows(entityList);
        return page;
    }

    /**
     * Convert Filters to ES query criteria
     *
     * @param filters Filters object
     * @return ES query criteria
     */
    /**
     * Translate a filter tree into a Criteria, keeping every group a group.
     *
     * <p>Each tree node becomes an AND or OR root and each child, leaf or group alike, hangs off it
     * as a sub-criteria — rendered as {@code bool.must} or {@code bool.should} respectively. The
     * chaining calls are not usable for this: {@code and(Criteria)} drops the child in as a chain
     * link, so a child that is itself a group arrives as a link with no field and the query is
     * refused ("criteria must have a field"); {@code or(Criteria)} keeps only the child's field,
     * so an OR whose first operand is a group loses that operand without a word.
     */
    Criteria convertFilters(Filters filters) {
        if (Filters.isEmpty(filters)) {
            return new Criteria();
        }
        if (FilterType.LEAF.equals(filters.getType())) {
            return convertFilterUnit(filters.getFilterUnit());
        }
        Criteria group = LogicOperator.OR.equals(filters.getLogicOperator()) ? Criteria.or() : Criteria.and();
        for (Filters child : filters.getChildren()) {
            if (!Filters.isEmpty(child)) {
                group.subCriteria(convertFilters(child));
            }
        }
        return group;
    }

    /**
     * Convert FilterUnit to ES query criteria
     *
     * @param filterUnit FilterUnit
     * @return ES query criteria
     */
    private Criteria convertFilterUnit(FilterUnit filterUnit) {
        // TODO: validate field metadata existence for ES object
        Assert.notTrue(filterUnit.isTuple(), "Elasticsearch does not support tuple filters: {0}", filterUnit);
        String field = filterUnit.getField();
        Object value = filterUnit.getValue();
        Criteria criteria = new Criteria(field);
        return switch (filterUnit.getOperator()) {
            case EQUAL -> criteria.is(value);
            case NOT_EQUAL -> criteria.not().is(value);
            case GREATER_THAN -> criteria.greaterThan(value);
            case GREATER_THAN_OR_EQUAL -> criteria.greaterThanEqual(value);
            case LESS_THAN -> criteria.lessThan(value);
            case LESS_THAN_OR_EQUAL -> criteria.lessThanEqual(value);
            case CONTAINS -> criteria.contains(value.toString());
            case NOT_CONTAINS -> criteria.not().contains(value.toString());
            case START_WITH -> criteria.startsWith(value.toString());
            case NOT_START_WITH -> criteria.not().startsWith(value.toString());
            case IN -> criteria.in((Collection<?>) value);
            case NOT_IN -> criteria.not().in((Collection<?>) value);
            case BETWEEN -> {
                List<?> values = (List<?>) value;
                yield criteria.between(values.get(0), values.get(1));
            }
            case NOT_BETWEEN -> {
                List<?> notValues = (List<?>) value;
                yield criteria.not().between(notValues.get(0), notValues.get(1));
            }
            case IS_SET -> criteria.exists();
            case IS_NOT_SET -> criteria.not().exists();
            case PARENT_OF -> buildParentOf(filterUnit);
            case CHILD_OF -> buildChildOf(filterUnit);
        };
    }

    /**
     * Build ES parent query condition
     * Extract the id collection from the idPath separated by "/", such as "1/2/3" -> [1, 2, 3]
     * @param filterUnit FilterUnit
     * @return ES parent query condition
     */
    private Criteria buildParentOf(FilterUnit filterUnit) {
        Set<Long> parentIds = new HashSet<>();
        for (Object path : (Collection<?>) filterUnit.getValue()) {
            parentIds.addAll(StringTools.splitIdPath((String) path));
        }
        return new Criteria(ModelConstant.ID).in(parentIds);
    }

    /**
     * Build ES child query condition
     * @param filterUnit FilterUnit
     * @return ES child query condition
     */
    private Criteria buildChildOf(FilterUnit filterUnit) {
        Criteria criteria = new Criteria();
        for (Object path : (Collection<?>) filterUnit.getValue()) {
            criteria = criteria.or(new Criteria(filterUnit.getField()).startsWith((String) path));
        }
        return criteria;
    }
}
