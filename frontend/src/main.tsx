import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './styles/global.css';

const container = document.getElementById('root');
if (!container) throw new Error('Не найден корневой элемент #root');

// global.css подключается один раз здесь; локальные стили заказов и категорий — в самих экранах.
createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
