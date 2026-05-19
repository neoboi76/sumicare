export type SortDirection = 'asc' | 'desc';
export type SortValueKind = 'auto' | 'string' | 'number' | 'date';

export interface SortState {
  readonly key: string | null;
  readonly direction: SortDirection;
}

export function toggleSort(current: SortState, key: string): SortState {
  if (current.key !== key) return { key, direction: 'asc' };
  if (current.direction === 'asc') return { key, direction: 'desc' };
  return { key: null, direction: 'asc' };
}

export function compareBy<T>(state: SortState, accessor: (row: T) => unknown, kind: SortValueKind = 'auto'): (a: T, b: T) => number {
  if (!state.key) return () => 0;
  const sign = state.direction === 'asc' ? 1 : -1;
  return (a, b) => {
    const va = accessor(a);
    const vb = accessor(b);
    return sign * compareValues(va, vb, kind);
  };
}

export function sortRows<T>(rows: readonly T[], state: SortState, resolver: (row: T) => unknown, kind: SortValueKind = 'auto'): T[] {
  if (!state.key) return [...rows];
  const sign = state.direction === 'asc' ? 1 : -1;
  return [...rows].sort((a, b) => sign * compareValues(resolver(a), resolver(b), kind));
}

function compareValues(a: unknown, b: unknown, kind: SortValueKind): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  if (kind === 'date') {
    const ta = new Date(a as string).getTime();
    const tb = new Date(b as string).getTime();
    return safeNum(ta) - safeNum(tb);
  }
  if (kind === 'number') {
    return safeNum(Number(a)) - safeNum(Number(b));
  }
  if (kind === 'string') {
    return String(a).localeCompare(String(b), undefined, { sensitivity: 'base', numeric: true });
  }
  if (typeof a === 'number' && typeof b === 'number') {
    return safeNum(a) - safeNum(b);
  }
  const na = Number(a);
  const nb = Number(b);
  if (!Number.isNaN(na) && !Number.isNaN(nb) && typeof a !== 'boolean' && typeof b !== 'boolean') {
    return na - nb;
  }
  return String(a).localeCompare(String(b), undefined, { sensitivity: 'base', numeric: true });
}

function safeNum(n: number): number {
  return Number.isFinite(n) ? n : 0;
}
