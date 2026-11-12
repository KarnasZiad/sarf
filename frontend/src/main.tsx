import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/public-sans/400.css';
import '@fontsource/public-sans/600.css';
import '@fontsource/public-sans/700.css';
import './styles.css';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Application } from './Application';
import { demarrerSession, utilisateurConnecte } from './auth';
import { Session } from './composants';

const racine = createRoot(document.getElementById('racine') as HTMLElement);
const clientRequetes = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: true, staleTime: 5_000 } },
});

racine.render(<div className="demande-session">Connexion…</div>);

demarrerSession()
  .then(() => {
    racine.render(
      <StrictMode>
        <Session.Provider value={utilisateurConnecte()}>
          <QueryClientProvider client={clientRequetes}>
            <BrowserRouter>
              <Application />
            </BrowserRouter>
          </QueryClientProvider>
        </Session.Provider>
      </StrictMode>,
    );
  })
  .catch(() => {
    racine.render(<div className="demande-session">Le service de connexion ne répond pas. Vérifiez qu'il est démarré, puis rechargez la page.</div>);
  });
