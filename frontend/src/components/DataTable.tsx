import type { ReactNode } from 'react'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
}

interface DataTableProps<T> {
  caption: string
  columns: readonly Column<T>[]
  rows: readonly T[]
  rowKey: (row: T) => string
  empty: string
}

/**
 * Admin list (UI-03): a table from 768 px on, below that every row becomes a card with labelled values. The labels
 * come from the column headers via data-label, so screen readers still get a real table.
 */
export function DataTable<T>({ caption, columns, rows, rowKey, empty }: DataTableProps<T>) {
  if (rows.length === 0) {
    return <p className="text-muted">{empty}</p>
  }
  return (
    <table className="data-table">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.header} scope="col">
              {column.header}
            </th>
          ))}
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
