enum ChronoUnit {
  seconds(singular: 'second', plural: 'seconds'),
  minutes(singular: 'minute', plural: 'minutes'),
  hours(singular: 'hour', plural: 'hours'),
  days(singular: 'day', plural: 'days');

  const ChronoUnit({required this.singular, required this.plural});
  final String singular;
  final String plural;
}
