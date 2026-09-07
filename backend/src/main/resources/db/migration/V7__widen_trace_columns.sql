-- Batch3 fix: merge trace stores customer names (name is VARCHAR(128) per V1),
-- widen trace value columns so merge/lifecycle traces never overflow.
ALTER TABLE crm_customer_trace ALTER COLUMN from_value TYPE VARCHAR(128);
ALTER TABLE crm_customer_trace ALTER COLUMN to_value   TYPE VARCHAR(255);
