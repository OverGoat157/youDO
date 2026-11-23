import { inspectPassword } from './passwordPolicy';
import './password.css';

export function PasswordRequirements({ password }: { password: string }) {
  const check = inspectPassword(password);
  const missing = 12 - check.bytes;
  const unit = check.ascii
    ? missing === 1 ? 'символ' : missing >= 2 && missing <= 4 ? 'символа' : 'символов'
    : missing === 1 ? 'байт UTF-8' : missing >= 2 && missing <= 4 ? 'байта UTF-8' : 'байт UTF-8';
  return <div className="password-requirements" id="password-policy">
    <p className="password-requirements__title">В новом пароле нужны:</p>
    <ul aria-label="Требования к новому паролю">
      {check.rules.map(rule => <li key={rule.id} className={rule.met ? 'password-rule--met' : ''}>
        <span aria-hidden="true" className="password-rule__mark">{rule.met ? '✓' : '○'}</span>
        <span className="visually-hidden">{rule.met ? 'Выполнено: ' : 'Не выполнено: '}</span>
        {rule.label}
      </li>)}
    </ul>
    <p id="password-length" className="password-length" role="status">
      Сейчас: {check.characters} символов{!check.ascii && `, ${check.bytes} байт UTF-8`}.
      {check.bytes < 12 && ` Нужно ещё минимум ${missing} ${unit}.`}
    </p>
    <p>Пример: <code>MangoRiver42!</code> — 13 символов и все нужные типы знаков.
      {' '}Придумайте свой пароль по этому образцу. Он должен отличаться от текущего.</p>
    <details>
      <summary>Если используете русские буквы или emoji</summary>
      <p>Они тоже разрешены. Общий размер пароля должен быть от 12 до 72 байт UTF-8:
        латинская буква занимает 1 байт, русская — 2, emoji — обычно 4. Счётчик выше учитывает это.</p>
    </details>
  </div>;
}
