enum FileSizeUnit {
  bytes(symbol: 'B'),
  kilobytes(symbol: 'kB'),
  megabytes(symbol: 'MB'),
  gigabytes(symbol: 'GB'),
  terabytes(symbol: 'TB'),
  petabytes(symbol: 'PB');

  const FileSizeUnit({required this.symbol});
  final String symbol;
}
