import { useState } from 'react';
import { api } from '../api/api';
import type { OrgChartNode } from '../api/types';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { useResource } from '../lib/useResource';

interface NodeProps {
  node: OrgChartNode;
  collapsed: Set<string>;
  toggle: (id: string) => void;
}

/** One person plus their subtree. Collapsible, and the whole tree is a real list tree. */
function OrgNode({ node, collapsed, toggle }: NodeProps): JSX.Element {
  const hasChildren = node.children.length > 0;
  const isCollapsed = collapsed.has(node.id);

  return (
    <li className="org-node">
      <div className="org-person">
        {hasChildren ? (
          <button
            type="button"
            className="org-toggle"
            aria-expanded={!isCollapsed}
            aria-label={
              isCollapsed
                ? `Expand the reports of ${node.fullName}`
                : `Collapse the reports of ${node.fullName}`
            }
            onClick={() => {
              toggle(node.id);
            }}
          >
            {isCollapsed ? '+' : '−'}
          </button>
        ) : (
          <span className="org-toggle org-toggle-leaf" aria-hidden="true" />
        )}
        <span className="org-name">{node.fullName}</span>
        <span className="org-meta">
          {node.jobTitle ?? 'No job title'}
          {node.departmentName === null ? '' : ` · ${node.departmentName}`}
        </span>
        {hasChildren && (
          <span className="org-count">
            {node.children.length} direct report{node.children.length === 1 ? '' : 's'}
          </span>
        )}
      </div>
      {hasChildren && !isCollapsed && (
        <ul className="org-children">
          {node.children.map((child) => (
            <OrgNode key={child.id} node={child} collapsed={collapsed} toggle={toggle} />
          ))}
        </ul>
      )}
    </li>
  );
}

export function OrgChartPage(): JSX.Element {
  const chart = useResource<OrgChartNode[]>((signal) => api.orgChart(signal), 'org-chart');
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  function toggle(id: string): void {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  return (
    <>
      <PageHeader
        title="Org chart"
        subtitle="The whole company tree. Pay is never part of this view."
      />

      <Card>
        {chart.loading && <Loading label="Loading the org chart…" />}
        {chart.error !== null && <Banner>{chart.error}</Banner>}
        {chart.data !== null && chart.data.length === 0 && (
          <EmptyState title="The org chart is empty." />
        )}
        {chart.data !== null && chart.data.length > 0 && (
          <ul className="org-tree" aria-label="Organisation chart">
            {chart.data.map((node) => (
              <OrgNode key={node.id} node={node} collapsed={collapsed} toggle={toggle} />
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}
