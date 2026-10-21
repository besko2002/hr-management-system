import { render, type RenderResult } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router';
import { App } from '../App';
import { Providers } from './Providers';

/** Renders the whole routed app at `path`, wrapped in the real providers. */
export function renderApp(path: string): RenderResult {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Providers>
        <App />
      </Providers>
    </MemoryRouter>,
  );
}

/** Same as {@link renderApp}, but under StrictMode so effects run twice. */
export function renderAppStrict(path: string): RenderResult {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <Providers>
          <App />
        </Providers>
      </MemoryRouter>
    </StrictMode>,
  );
}

