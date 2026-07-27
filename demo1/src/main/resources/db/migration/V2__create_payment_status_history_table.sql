CREATE TABLE payment_status_history (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    triggered_by VARCHAR(50) NOT NULL,
    error_code VARCHAR(50),
    notes VARCHAR(255),
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_payment_status_history_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_payment_id ON payment_status_history(payment_id);
CREATE INDEX idx_payment_changed_at ON payment_status_history(payment_id, changed_at);

