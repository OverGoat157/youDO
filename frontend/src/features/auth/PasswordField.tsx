import { useState, type InputHTMLAttributes } from 'react';
import './password.css';

type PasswordFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'id'> & {
  id: string;
  label: string;
};

export function PasswordField({ id, label, disabled, ...input }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  return <>
    <label htmlFor={id}>{label}</label>
    <div className="password-input">
      <input {...input} id={id} type={visible ? 'text' : 'password'} disabled={disabled}
        autoCapitalize="none" spellCheck={false} />
      <button type="button" className="password-toggle" disabled={disabled}
        aria-label={`${visible ? 'Скрыть' : 'Показать'} пароль: ${label.toLowerCase()}`}
        aria-controls={id} aria-pressed={visible} onClick={() => setVisible(value => !value)}>
        {visible ? 'Скрыть' : 'Показать'}
      </button>
    </div>
  </>;
}
