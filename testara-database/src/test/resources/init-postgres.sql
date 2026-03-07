CREATE TABLE msg_message_sent (
    id SERIAL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    body TEXT,
    channel VARCHAR(50) DEFAULT 'email',
    status VARCHAR(50) DEFAULT 'sent',
    sent_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO msg_message_sent (recipient, subject, body, channel, status) VALUES
    ('user1@test.com', 'Welcome', 'Welcome to testara', 'email', 'sent'),
    ('user2@test.com', 'Alert', 'System alert notification', 'push', 'delivered');
