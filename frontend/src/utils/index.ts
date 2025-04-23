/**
 * 格式化日期时间
 * @param dateStr 日期字符串
 * @param format 格式化模板
 */
export function formatDateTime(dateStr: string, format: string = 'YYYY-MM-DD HH:mm'): string {
  if (!dateStr) return '';
  
  const date = new Date(dateStr);
  
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  
  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
}

/**
 * 创建防抖函数
 * @param fn 需要防抖的函数
 * @param delay 延迟时间，单位毫秒
 */
export function debounce<T extends (...args: any[]) => any>(fn: T, delay = 300): (...args: Parameters<T>) => void {
  let timer: number | null = null;
  
  return function(this: any, ...args: Parameters<T>): void {
    if (timer) {
      clearTimeout(timer);
    }
    
    timer = window.setTimeout(() => {
      fn.apply(this, args);
      timer = null;
    }, delay);
  };
}

/**
 * 截取字符串并添加省略号
 * @param str 字符串
 * @param len 保留长度
 */
export function truncateText(str: string, len = 100): string {
  if (!str) return '';
  if (str.length <= len) return str;
  
  return str.slice(0, len) + '...';
}

/**
 * 格式化数字，如将1000格式化为1k
 * @param num 数字
 */
export function formatNumber(num: number | null | undefined): string {
  if (num === null || num === undefined) return '0';
  if (isNaN(Number(num))) return '0';
  
  const numVal = Number(num);
  if (numVal < 1000) return String(numVal);
  if (numVal < 1000000) return (numVal / 1000).toFixed(1) + 'k';
  return (numVal / 1000000).toFixed(1) + 'M';
} 