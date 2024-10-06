INSERT INTO PUBLIC_INFO (CONTENTS) VALUES ('authorized user can access to this info');
INSERT INTO SECRET_INFO (CONTENTS) VALUES ('this info can be accessed only if authorized user who has ADMIN ROLE');

COMMIT;