INSERT INTO academic_years (name, starts_on, ends_on, is_current)
VALUES ('2026-27', '2026-04-01', '2027-03-31', TRUE);

INSERT INTO class_sections (academic_year_id, grade, name)
SELECT years.id, 'Grade ' || grades.grade_number, 'A'
FROM academic_years years
CROSS JOIN generate_series(1, 12) AS grades(grade_number)
WHERE years.name = '2026-27';
