CREATE TABLE IF NOT EXISTS links (
  shortlink VARCHAR(256) PRIMARY KEY,
  target TEXT,
  updated_by VARCHAR(256),
  usage INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS links_t ON links (target);
CREATE INDEX IF NOT EXISTS links_u ON links (usage);

CREATE TABLE IF NOT EXISTS history (
  id SERIAL PRIMARY KEY,
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(256),
  shortlink VARCHAR(256),
  target TEXT
);

CREATE INDEX IF NOT EXISTS history_sl ON history (shortlink);
CREATE INDEX IF NOT EXISTS history_ub ON history (updated_by);
