const groups = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnopqrstuvwxyz', '23456789', '!@#$%&*+-=?'];
function randomIndex(limit: number): number {
  const bytes = new Uint32Array(1);
  const ceiling = Math.floor(0x100000000 / limit) * limit;
  do { crypto.getRandomValues(bytes); } while (bytes[0] >= ceiling);
  return bytes[0] % limit;
}
/** 20 ASCII bytes, all four required classes; rejection sampling avoids modulo bias. */
export function generateTemporaryPassword(): string {
  const alphabet = groups.join('');
  const chars = groups.map(group => group[randomIndex(group.length)]);
  while (chars.length < 20) chars.push(alphabet[randomIndex(alphabet.length)]);
  for (let i = chars.length - 1; i > 0; i--) {
    const j = randomIndex(i + 1); [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}
