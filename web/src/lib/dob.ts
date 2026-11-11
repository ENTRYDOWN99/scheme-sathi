export type DobValidationStatus =
  | 'INCOMPLETE'
  | 'INVALID_FORMAT'
  | 'INVALID_CALENDAR_DATE'
  | 'FUTURE_DATE'
  | 'AGE_OUT_OF_RANGE'
  | 'VALID';

export function formatDobWithSlashes(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
}

export function isoDateToDdMmYyyy(isoDate: string): string {
  const parts = isoDate.split('-');
  if (parts.length !== 3) return '';
  return `${parts[2]}/${parts[1]}/${parts[0]}`;
}

export function ddMmYyyyToIsoDate(dob: string): string {
  const parts = dob.split('/');
  if (parts.length !== 3 || parts[2].length !== 4) return '';
  return `${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}`;
}

export function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

export function getDaysInMonth(month: number, year: number): number {
  switch (month) {
    case 1:
    case 3:
    case 5:
    case 7:
    case 8:
    case 10:
    case 12:
      return 31;
    case 4:
    case 6:
    case 9:
    case 11:
      return 30;
    case 2:
      return isLeapYear(year) ? 29 : 28;
    default:
      return 0;
  }
}

export function validateDob(dobStr: string, refDate: Date = new Date()): DobValidationStatus {
  const trimmed = dobStr.trim();
  if (!trimmed) return 'INCOMPLETE';
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(trimmed);
  if (!match) {
    return trimmed.length < 10 ? 'INCOMPLETE' : 'INVALID_FORMAT';
  }
  const day = Number.parseInt(match[1], 10);
  const month = Number.parseInt(match[2], 10);
  const year = Number.parseInt(match[3], 10);

  if (month < 1 || month > 12) return 'INVALID_CALENDAR_DATE';
  const maxDay = getDaysInMonth(month, year);
  if (day < 1 || day > maxDay) return 'INVALID_CALENDAR_DATE';

  const curY = refDate.getFullYear();
  const curM = refDate.getMonth() + 1;
  const curD = refDate.getDate();

  if (year > curY || (year === curY && (month > curM || (month === curM && day > curD)))) {
    return 'FUTURE_DATE';
  }

  let age = curY - year;
  if (curM < month || (curM === month && curD < day)) {
    age -= 1;
  }
  if (age < 0 || age > 120) return 'AGE_OUT_OF_RANGE';
  return 'VALID';
}

export function calculateAgeFromDob(dobStr: string, refDate: Date = new Date()): number | null {
  if (validateDob(dobStr, refDate) !== 'VALID') return null;
  const [dd, mm, yyyy] = dobStr.split('/').map((n) => Number.parseInt(n, 10));
  let age = refDate.getFullYear() - yyyy;
  const m = refDate.getMonth() + 1;
  const d = refDate.getDate();
  if (m < mm || (m === mm && d < dd)) {
    age -= 1;
  }
  return age;
}
