CREATE TABLE IF NOT EXISTS students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    admission_number TEXT,
    name TEXT NOT NULL,
    grade TEXT NOT NULL,
    email TEXT NOT NULL,
    date_of_birth TEXT,
    guardian_name TEXT,
    guardian_email TEXT,
    guardian_phone TEXT,
    address TEXT,
    enrollment_date TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);
