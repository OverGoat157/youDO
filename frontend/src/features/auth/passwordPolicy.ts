// Same Unicode character classes and UTF-8 limits as the backend PasswordPolicy.
const javaWhitespace = /[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]/u;

export function inspectPassword(password: string) {
  const characters = Array.from(password);
  const bytes = new TextEncoder().encode(password).length;
  const ascii = characters.every(character => character.codePointAt(0)! <= 0x7f);
  const validText = !characters.some(character => character === '\0'
    || character.length === 1 && /[\ud800-\udfff]/u.test(character));
  const rules = [
    { id: 'length', met: bytes >= 12 && bytes <= 72,
      label: ascii ? 'От 12 до 72 символов' : 'Размер от 12 до 72 байт UTF-8',
      problem: bytes < 12
        ? ascii ? `Пароль слишком короткий: ${characters.length} символов. Нужно минимум 12.`
          : `Пароль слишком короткий: ${bytes} байт UTF-8. Нужно минимум 12.`
        : `Пароль слишком длинный: ${bytes} байт UTF-8. Допускается не больше 72.` },
    { id: 'uppercase', met: /\p{Uppercase}/u.test(password), label: 'Заглавная буква, например A',
      problem: 'Добавьте заглавную букву, например A.' },
    { id: 'lowercase', met: /\p{Lowercase}/u.test(password), label: 'Строчная буква, например a',
      problem: 'Добавьте строчную букву, например a.' },
    { id: 'digit', met: /\p{Nd}/u.test(password), label: 'Цифра, например 7',
      problem: 'Добавьте хотя бы одну цифру.' },
    { id: 'special', met: characters.some(character => !/[\p{L}\p{Nd}]/u.test(character) && !javaWhitespace.test(character)),
      label: 'Спецсимвол, например ! @ # или ?', problem: 'Добавьте спецсимвол, например !, @, # или ?.' },
  ];
  const problems = rules.filter(rule => !rule.met).map(rule => rule.problem);
  if (!validText) problems.push('Удалите недопустимый символ из пароля.');
  return { characters: characters.length, bytes, ascii, rules, problems };
}
