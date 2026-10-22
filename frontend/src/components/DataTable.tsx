import type { ReactNode } from 'react'
import { texts } from '../i18n/de'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
  /** Sort field of the API (whitelist); without it the column does not sort. */
  sortField?: string
}

export interface Sort {
  field: string
  direction: 'asc' | 'desc'
}

interface DataTableProps<T> {
  caption: string
  columns: readonly Column<T>[]
  rows: readonly T[]
  rowKey: (row: T) => string
  empty: string
  sort?: Sort
  /** Called with the column's sort field; the caller decides the direction. */
  onSort?: (field: string) => void
}

/**
 * Admin list (UI-03): a table from 768 px on, below that every row becomes a card with labelled values. The labels
 * come from the column headers via data-label, so screen readers still get a real table. Sortable columns have a
 * button in the header and `aria-sort` (UI-09, UI-11).
 */
export function DataTable<T>({ caption, columns, rows, rowKey, empty, sort, onSort }: DataTableProps<T>) {
  if (rows.length === 0) {
    return <p className="text-muted">{empty}</p>
  }
  return (
    <table className="data-table">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          {columns.map((column) => {
            const active = sort !== undefined && column.sortField === sort.field
            return (
              <th
                key={column.header}
                scope="col"
                aria-sort={active ? (sort.direction === 'asc' ? 'ascending' : 'descending') : undefined}
              >
                {column.sortField === undefined || onSort === undefined ? (
                  column.header
                ) : (
                  <button
                    type="button"
                    className="sort-button"
                    onClick={() => {
                      onSort(column.sortField ?? '')
                    }}
                  >
                    {column.header}
                    <span aria-hidden="true" className="sort-button__arrow">
                      {active ? (sort.direction === 'asc' ? '▲' : '▼') : '↕'}
                    </span>
                    <span className="sr-only">{texts.admin.sortBy}</span>
                  </button>
                )}
              </th>
            )
          })}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={rowKey(row)}>
            {columns.map((column) => (
              <td key={column.header} data-label={column.header}>
                {column.cell(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
