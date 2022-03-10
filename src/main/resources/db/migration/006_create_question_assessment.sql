CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE table IF NOT EXISTS question
(
    id              uuid DEFAULT uuid_generate_v4 (),
    form_id         VARCHAR(100) NOT NULL,
    form_version    VARCHAR(10),
    x_path          VARCHAR(500) NOT NULL,
    question_type   VARCHAR(500) NOT NULL,
    meta            JSONB,
    created         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_form ON question(form_id, form_version);
CREATE INDEX IF NOT EXISTS idx_xPath ON question(x_path);
CREATE INDEX IF NOT EXISTS idx_ques_type ON question(question_type);

CREATE table IF NOT EXISTS assessment
(
    id              uuid DEFAULT uuid_generate_v4 (),
    question        uuid,
    answer          text,
    bot_id          uuid,
    user_id         uuid,
    device_id       uuid,
    meta            JSONB,
    created         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_question
        FOREIGN KEY (question)
            REFERENCES question(id)
);

CREATE INDEX IF NOT EXISTS idx_assessment_bot_id ON assessment(bot_id);
CREATE INDEX IF NOT EXISTS idx_assessment_user_id ON assessment(user_id);
CREATE INDEX IF NOT EXISTS idx_assessment_device_id ON assessment(device_id);