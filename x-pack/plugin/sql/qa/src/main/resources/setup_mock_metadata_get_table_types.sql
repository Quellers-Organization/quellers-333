CREATE TABLE mock (
  TABLE_TYPE VARCHAR,
) AS
SELECT 'BASE TABLE' FROM DUAL
UNION ALL
SELECT 'VIEW' FROM DUAL
;
