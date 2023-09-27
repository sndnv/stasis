Duration durationFromJson(int duration) => Duration(seconds: duration);

int durationToJson(Duration duration) => duration.inSeconds;

DateTime dateTimeFromJson(String dateTime) => DateTime.parse(dateTime);

String dateTimeToJson(DateTime dateTime) => dateTime.toIso8601String();
