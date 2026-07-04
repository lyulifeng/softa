package io.softa.starter.referencedata.service.impl;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.referencedata.entity.Currency;
import io.softa.starter.referencedata.service.CurrencyService;
import io.softa.starter.referencedata.support.CurrencyCache;

@Service
public class CurrencyServiceImpl extends EntityServiceImpl<Currency, String>
        implements CurrencyService {

    @Autowired
    private CurrencyCache cache;

    @Override
    public Optional<Currency> findByCode(String code) {
        // code-as-id: the ISO 4217 code IS the primary key.
        return Optional.ofNullable(cache.getByCode(code, () -> this.getById(code).orElse(null)));
    }

    @Override
    public boolean updateOne(Currency entity) {
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
