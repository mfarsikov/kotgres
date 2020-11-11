package kotgres.kapt.mapper

import kotgres.aux.PostgresType
import kotgres.kapt.parser.KotlinType

val kotlinTypeToPostgresTypeMapping = mapOf(
    KotlinType.BIG_DECIMAL to PostgresType.NUMERIC,
    KotlinType.BOOLEAN to PostgresType.BOOLEAN,
    KotlinType.BYTE_ARRAY to PostgresType.BYTEA,
    KotlinType.DATE to PostgresType.DATE,
    KotlinType.DOUBLE to PostgresType.DOUBLE,
    KotlinType.FLOAT to PostgresType.REAL,
    KotlinType.INT to PostgresType.INTEGER,
    KotlinType.LIST to PostgresType.JSONB,
    KotlinType.LONG to PostgresType.BIGINT,
    KotlinType.LOCAL_DATE to PostgresType.DATE,
    KotlinType.LOCAL_DATE_TIME to PostgresType.TIMESTAMP,
    KotlinType.LOCAL_TIME to PostgresType.TIME,
    KotlinType.MAP to PostgresType.JSONB,
    KotlinType.STRING to PostgresType.TEXT,
    KotlinType.TIME to PostgresType.TIME,
    KotlinType.TIMESTAMP to PostgresType.TIMESTAMP_WITH_TIMEZONE,
    KotlinType.UUID to PostgresType.UUID,
)
