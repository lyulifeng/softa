-- ============================================================
-- reference-data-starter DDL — PostgreSQL. GENERATED from the @Model annotations,
-- do not hand-edit. The MySQL counterpart (reference-data-starter.sql) is the
-- hand-written original and is intentionally left untouched.
--
-- Source of truth is the entity model, never this file. To regenerate, render the
-- @Model/@Field/@Index annotations through the PostgreSQL DDL dialect:
--   ClasspathScannerSupport -> AnnotationParser.parse
--     -> ReferenceColumnResolver.stampSysFields   (TO_ONE FKs mirror the referenced id)
--     -> SysDdlContextBuilder.forCreate
--     -> DdlDialectFactory.create(DatabaseType.POSTGRESQL, BuiltinDdlMetadataResolver.INSTANCE)
-- See apps/demo-app/src/test/java/io/softa/app/metadata/MetadataBaselineDdlGeneratorTest
-- for the same chain against MySQL. CREATE TABLE / CREATE INDEX are patched to
-- IF NOT EXISTS after rendering (the template stays untouched -- it also drives
-- runtime auto-DDL).
--
-- Not read at boot when scanner-scope is non-empty — the annotation
-- lane creates these tables itself. This file exists for runtimes
-- with an empty scanner-scope (production) and for DBA review.
-- ============================================================

-- CountryRegion
/* Create table for model: Country / Region */
CREATE TABLE IF NOT EXISTS country_region (
    id VARCHAR(2) NOT NULL DEFAULT '',
    name VARCHAR(100) NOT NULL DEFAULT '',
    alpha3_code VARCHAR(3) NOT NULL DEFAULT '',
    dial_code VARCHAR(8) NOT NULL DEFAULT '',
    currency_code VARCHAR(3) NOT NULL DEFAULT '',
    continent VARCHAR(64) NOT NULL DEFAULT '',
    eea BOOLEAN,
    has_subdivisions BOOLEAN,
    created_time TIMESTAMP,
    created_id BIGINT,
    created_by VARCHAR(64),
    updated_time TIMESTAMP,
    updated_id BIGINT,
    updated_by VARCHAR(64),
    PRIMARY KEY (id)
);
COMMENT ON TABLE country_region IS 'ISO 3166-1 alpha-2 country/region master';
COMMENT ON COLUMN country_region.id IS 'ISO 3166-1 alpha-2 code (CN/US/TW/...); primary key = the code';
COMMENT ON COLUMN country_region.name IS 'ISO 3166-1 standard English short name';
COMMENT ON COLUMN country_region.alpha3_code IS 'ISO 3166-1 alpha-3 (CHN/USA/TWN); 3-letter code for SWIFT / Stripe';
COMMENT ON COLUMN country_region.dial_code IS 'ITU-T E.164 country dial code, digits only (no leading +)';
COMMENT ON COLUMN country_region.currency_code IS 'Default currency — FK to currency.id (ISO 4217 alpha-3, code-as-id)';
COMMENT ON COLUMN country_region.continent IS 'Continent (7-continent model)';
COMMENT ON COLUMN country_region.eea IS 'EEA / EU member flag — GDPR scope, VAT reverse charge eligibility';
COMMENT ON COLUMN country_region.has_subdivisions IS 'True if country_subdivision rows exist for this country';
CREATE INDEX IF NOT EXISTS idx_continent ON country_region (continent);
CREATE INDEX IF NOT EXISTS idx_currency_code ON country_region (currency_code);
CREATE INDEX IF NOT EXISTS idx_eea ON country_region (eea);

-- CountrySubdivision
/* Create table for model: Country Subdivision */
CREATE TABLE IF NOT EXISTS country_subdivision (
    id VARCHAR(10) NOT NULL DEFAULT '',
    country_code VARCHAR(2) NOT NULL DEFAULT '',
    name VARCHAR(100) NOT NULL DEFAULT '',
    parent_code VARCHAR(10),
    type VARCHAR(20),
    created_time TIMESTAMP,
    created_id BIGINT,
    created_by VARCHAR(64),
    updated_time TIMESTAMP,
    updated_id BIGINT,
    updated_by VARCHAR(64),
    PRIMARY KEY (id)
);
COMMENT ON TABLE country_subdivision IS 'ISO 3166-2 country subdivisions';
COMMENT ON COLUMN country_subdivision.id IS 'ISO 3166-2 full code (CN-31 / US-CA / JP-13); primary key = the code';
COMMENT ON COLUMN country_subdivision.country_code IS 'Owning country — FK to country_region.id (ISO 3166-1 alpha-2, code-as-id)';
COMMENT ON COLUMN country_subdivision.name IS 'English name';
COMMENT ON COLUMN country_subdivision.parent_code IS 'Parent subdivision for hierarchical regions — FK to country_subdivision.id (self-relation); null for top-level';
COMMENT ON COLUMN country_subdivision.type IS 'Subdivision type — province / state / prefecture / region / municipality / county';
CREATE INDEX IF NOT EXISTS idx_country ON country_subdivision (country_code);
CREATE INDEX IF NOT EXISTS idx_parent ON country_subdivision (parent_code);

-- Currency
/* Create table for model: Currency */
CREATE TABLE IF NOT EXISTS currency (
    id VARCHAR(3) NOT NULL DEFAULT '',
    numeric_code VARCHAR(3) NOT NULL DEFAULT '',
    name VARCHAR(100) NOT NULL DEFAULT '',
    symbol VARCHAR(10) NOT NULL DEFAULT '',
    decimal_places INT NOT NULL,
    created_time TIMESTAMP,
    created_id BIGINT,
    created_by VARCHAR(64),
    updated_time TIMESTAMP,
    updated_id BIGINT,
    updated_by VARCHAR(64),
    PRIMARY KEY (id)
);
COMMENT ON TABLE currency IS 'ISO 4217 currency master';
COMMENT ON COLUMN currency.id IS 'ISO 4217 alpha-3 code (USD/CNY/EUR/...); primary key = the code';
COMMENT ON COLUMN currency.numeric_code IS 'ISO 4217 numeric, 3 digits with leading zero (840/156/048)';
COMMENT ON COLUMN currency.name IS 'English name, e.g. ''US Dollar''';
COMMENT ON COLUMN currency.symbol IS 'Unicode display symbol ($ / ¥ / € / ₹ / £ / ...)';
COMMENT ON COLUMN currency.decimal_places IS 'ISO 4217 fraction digits — 0 for JPY/KRW, 2 for USD/EUR/CNY, 3 for BHD/KWD/IQD. CRITICAL for monetary arithmetic';

