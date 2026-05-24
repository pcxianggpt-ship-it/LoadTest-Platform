export function formatDisplayDateTime(value?: string | null): string {
  if (!value) {
    return "";
  }

  const normalizedValue = value.replace(/(\.\d{3})\d+/, "$1");
  const date = new Date(normalizedValue);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hours = pad(date.getHours());
  const minutes = pad(date.getMinutes());
  const seconds = pad(date.getSeconds());

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function pad(value: number): string {
  return value.toString().padStart(2, "0");
}
