export type DobValidationStatus =
  | 'VALID'
  | 'INCOMPLETE'
  | 'INVALID_FORMAT'
  | 'INVALID_CALENDAR_DATE'
  | 'FUTURE_DATE'
  | 'AGE_OUT_OF_RANGE';

export interface ParsedDob {
  day: number;
  month: number;
  year: number;
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

export function parseDob(dobStr: string): ParsedDob | null {
  const trimmed = dobStr.trim();
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(trimmed);
  if (!match) return null;
  return {
    day: Number(match[1]),
    month: Number(match[2]),
    year: Number(match[3])
  };
}

export function calculateAge(
  dobStr: string,
  refDate: Date = new Date()
): number {
  const parsed = parseDob(dobStr);
  if (!parsed) return -1;
  if (parsed.month < 1 || parsed.month > 12) return -1;
  const maxDays = getDaysInMonth(parsed.month, parsed.year);
  if (parsed.day < 1 || parsed.day > maxDays) return -1;

  const refDay = refDate.getDate();
  const refMonth = refDate.getMonth() + 1;
  const refYear = refDate.getFullYear();

  let age = refYear - parsed.year;
  if (refMonth < parsed.month || (refMonth === parsed.month && refDay < parsed.day)) {
    age -= 1;
  }
  return age;
}

export function validateDob(
  dobStr: string,
  refDate: Date = new Date()
): DobValidationStatus {
  const trimmed = dobStr.trim();
  if (!trimmed || trimmed.length < 10) return 'INCOMPLETE';
  const parsed = parseDob(trimmed);
  if (!parsed) return 'INVALID_FORMAT';
  if (parsed.month < 1 || parsed.month > 12) return 'INVALID_CALENDAR_DATE';
  const maxDays = getDaysInMonth(parsed.month, parsed.year);
  if (parsed.day < 1 || parsed.day > maxDays) return 'INVALID_CALENDAR_DATE';

  const refDay = refDate.getDate();
  const refMonth = refDate.getMonth() + 1;
  const refYear = refDate.getFullYear();

  const isFuture =
    parsed.year > refYear ||
    (parsed.year === refYear && parsed.month > refMonth) ||
    (parsed.year === refYear && parsed.month === refMonth && parsed.day > refDay);

  if (isFuture) return 'FUTURE_DATE';

  const age = calculateAge(trimmed, refDate);
  if (age < 0 || age > 120) return 'AGE_OUT_OF_RANGE';

  return 'VALID';
}
