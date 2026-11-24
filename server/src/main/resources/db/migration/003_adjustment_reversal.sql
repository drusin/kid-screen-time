ALTER TABLE daily_adjustments ADD COLUMN reverses_id INTEGER REFERENCES daily_adjustments (id)
