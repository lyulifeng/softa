package io.softa.starter.referencedata.service.impl;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.referencedata.entity.CountrySubdivision;
import io.softa.starter.referencedata.service.CountrySubdivisionService;

@Service
public class CountrySubdivisionServiceImpl extends EntityServiceImpl<CountrySubdivision, String>
        implements CountrySubdivisionService {

    @Override
    public Optional<CountrySubdivision> findByCode(String code) {
        // code-as-id: the ISO 3166-2 full code IS the primary key.
        return this.getById(code);
    }

    @Override
    public List<CountrySubdivision> findByCountryCode(String countryCode) {
        Filters filters = new Filters().eq(CountrySubdivision::getCountryCode, countryCode);
        return this.searchList(new FlexQuery(filters, Orders.ofAsc(CountrySubdivision::getId)));
    }

    @Override
    public List<CountrySubdivision> findByParentCode(String parentCode) {
        Filters filters = new Filters().eq(CountrySubdivision::getParentCode, parentCode);
        return this.searchList(new FlexQuery(filters, Orders.ofAsc(CountrySubdivision::getId)));
    }
}
