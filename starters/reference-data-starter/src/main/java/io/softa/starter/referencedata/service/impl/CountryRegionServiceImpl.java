package io.softa.starter.referencedata.service.impl;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.referencedata.entity.CountryRegion;
import io.softa.starter.referencedata.enums.Continent;
import io.softa.starter.referencedata.service.CountryRegionService;
import io.softa.starter.referencedata.support.CountryRegionCache;

@Service
public class CountryRegionServiceImpl extends EntityServiceImpl<CountryRegion, String>
        implements CountryRegionService {

    @Autowired
    private CountryRegionCache cache;

    @Override
    public Optional<CountryRegion> findByCode(String code) {
        // code-as-id: the ISO 3166-1 alpha-2 code IS the primary key.
        return Optional.ofNullable(cache.getByCode(code, () -> this.getById(code).orElse(null)));
    }

    @Override
    public List<CountryRegion> findByContinent(Continent continent) {
        Filters filters = new Filters().eq(CountryRegion::getContinent, continent);
        return this.searchList(new FlexQuery(filters, Orders.ofAsc(CountryRegion::getId)));
    }

    @Override
    public List<CountryRegion> findEeaMembers() {
        Filters filters = new Filters().eq(CountryRegion::getEea, true);
        return this.searchList(new FlexQuery(filters, Orders.ofAsc(CountryRegion::getId)));
    }

    @Override
    public boolean updateOne(CountryRegion entity) {
        boolean result = super.updateOne(entity);
        if (result) cache.evictByCode(entity.getId());
        return result;
    }

    @Override
    public boolean deleteById(String id) {
        boolean result = super.deleteById(id);
        if (result) cache.evictByCode(id);   // id IS the code
        return result;
    }
}
