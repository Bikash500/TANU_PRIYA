INSERT INTO students (name, grade, email)
SELECT 'Olivia Chen', 'Grade 10', 'olivia.chen@school.edu'
WHERE NOT EXISTS (SELECT 1 FROM students);

INSERT INTO students (name, grade, email)
SELECT 'Noah Williams', 'Grade 9', 'noah.williams@school.edu'
WHERE (SELECT COUNT(*) FROM students) = 1;

INSERT INTO students (name, grade, email)
SELECT 'Ava Patel', 'Grade 11', 'ava.patel@school.edu'
WHERE (SELECT COUNT(*) FROM students) = 2;

INSERT INTO students (name, grade, email)
SELECT 'Liam Johnson', 'Grade 8', 'liam.johnson@school.edu'
WHERE (SELECT COUNT(*) FROM students) = 3;
