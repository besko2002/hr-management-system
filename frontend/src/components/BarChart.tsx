export interface BarDatum {
  label: string;
  value: number;
  /** Shown instead of the raw number, e.g. money with separators. */
  display?: string;
}

interface BarChartProps {
  title: string;
  data: BarDatum[];
  /** Appended to the accessible description of each bar, e.g. "employees". */
  unit?: string;
  emptyLabel?: string;
}

const BAR_HEIGHT = 26;
const GAP = 10;
const LABEL_WIDTH = 170;
const TRACK_WIDTH = 320;

/**
 * A horizontal bar chart in plain SVG — no chart library. The SVG is decorative
 * (`aria-hidden`) and the real content is the table below it, so the numbers are
 * readable by a screen reader and selectable by anybody.
 */
export function BarChart({ title, data, unit = '', emptyLabel = 'No data yet.' }: BarChartProps): JSX.Element {
  if (data.length === 0) {
    return (
      <figure className="chart">
        <figcaption className="chart-title">{title}</figcaption>
        <p className="empty-hint">{emptyLabel}</p>
      </figure>
    );
  }

  const max = data.reduce((best, item) => Math.max(best, item.value), 0);
  const height = data.length * BAR_HEIGHT + (data.length - 1) * GAP;

  return (
    <figure className="chart">
      <figcaption className="chart-title">{title}</figcaption>
      <svg
        className="chart-svg"
        viewBox={`0 0 ${String(LABEL_WIDTH + TRACK_WIDTH + 70)} ${String(height)}`}
        width="100%"
        height={height}
        aria-hidden="true"
        focusable="false"
      >
        {data.map((item, index) => {
          const y = index * (BAR_HEIGHT + GAP);
          const width = max === 0 ? 0 : Math.max(2, (item.value / max) * TRACK_WIDTH);
          return (
            <g key={`${item.label}-${String(index)}`}>
              <text x={0} y={y + BAR_HEIGHT / 2 + 5} className="chart-label">
                {item.label}
              </text>
              <rect
                x={LABEL_WIDTH}
                y={y}
                width={TRACK_WIDTH}
                height={BAR_HEIGHT}
                rx={4}
                className="chart-track"
              />
              <rect
                x={LABEL_WIDTH}
                y={y}
                width={width}
                height={BAR_HEIGHT}
                rx={4}
                className="chart-bar"
              />
              <text
                x={LABEL_WIDTH + TRACK_WIDTH + 8}
                y={y + BAR_HEIGHT / 2 + 5}
                className="chart-value"
              >
                {item.display ?? String(item.value)}
              </text>
            </g>
          );
        })}
      </svg>
      <table className="table table-compact chart-table">
        <caption className="sr-only">{title}</caption>
        <thead>
          <tr>
            <th scope="col">Series</th>
            <th scope="col">{unit.length > 0 ? unit : 'Value'}</th>
          </tr>
        </thead>
        <tbody>
          {data.map((item, index) => (
            <tr key={`${item.label}-row-${String(index)}`}>
              <th scope="row">{item.label}</th>
              <td>{item.display ?? String(item.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
