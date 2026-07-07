import Foundation

enum CalendarICal {
    static func data(from event: CalendarEvent, uid: String, stamp: Date) -> Data {
        Data(text(from: event, uid: uid, stamp: stamp).utf8)
    }

    static func text(from event: CalendarEvent, uid: String, stamp: Date) -> String {
        var lines: [String] = [
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//stasis//iOS//EN",
            "CALSCALE:GREGORIAN",
            "BEGIN:VEVENT",
            "UID:\(uid)",
            "DTSTAMP:\(utcDateTime(stamp))"
        ]
        appendBoundaries(&lines, event)
        lines.append(field("SUMMARY", event.title))
        appendIfPresent(&lines, "LOCATION", event.location)
        appendIfPresent(&lines, "DESCRIPTION", event.notes)
        if let url = event.url, !url.isEmpty { lines.append("URL:\(url)") }
        lines.append("TRANSP:\(event.availability == .free ? "TRANSPARENT" : "OPAQUE")")
        for rule in event.recurrenceRules { lines.append("RRULE:\(rrule(rule))") }
        for alarm in event.alarms { lines.append(contentsOf: valarm(alarm, summary: event.title)) }
        lines.append("END:VEVENT")
        lines.append("END:VCALENDAR")
        return lines.map(fold).joined(separator: "\r\n") + "\r\n"
    }

    private static func appendBoundaries(_ lines: inout [String], _ event: CalendarEvent) {
        if event.isAllDay {
            let zone = event.timeZone.flatMap { TimeZone(identifier: $0) } ?? .current
            let startDay = date(event.start, zone: zone)
            var endDay = date(event.end, zone: zone)
            if endDay <= startDay {
                endDay = date(event.start + 86_400, zone: zone)
            }
            lines.append("DTSTART;VALUE=DATE:\(startDay)")
            lines.append("DTEND;VALUE=DATE:\(endDay)")
        } else {
            lines.append("DTSTART:\(utcDateTime(seconds: event.start))")
            lines.append("DTEND:\(utcDateTime(seconds: event.end))")
        }
    }

    private static func rrule(_ recurrence: CalendarEvent.Recurrence) -> String {
        var parts = ["FREQ=\(frequency(recurrence.frequency))"]
        if recurrence.interval > 1 { parts.append("INTERVAL=\(recurrence.interval)") }
        if !recurrence.daysOfWeek.isEmpty {
            parts.append("BYDAY=\(recurrence.daysOfWeek.map(byDay).joined(separator: ","))")
        }
        appendList(&parts, "BYMONTHDAY", recurrence.daysOfMonth)
        appendList(&parts, "BYYEARDAY", recurrence.daysOfYear)
        appendList(&parts, "BYWEEKNO", recurrence.weeksOfYear)
        appendList(&parts, "BYMONTH", recurrence.monthsOfYear)
        appendList(&parts, "BYSETPOS", recurrence.setPositions)
        switch recurrence.end {
        case .occurrenceCount(let count): parts.append("COUNT=\(count)")
        case .endDate(let seconds): parts.append("UNTIL=\(utcDateTime(seconds: seconds))")
        case nil: break
        }
        return parts.joined(separator: ";")
    }

    private static func valarm(_ alarm: CalendarEvent.Alarm, summary: String) -> [String] {
        let trigger: String
        if let absolute = alarm.absoluteDate {
            trigger = "TRIGGER;VALUE=DATE-TIME:\(utcDateTime(seconds: absolute))"
        } else {
            trigger = "TRIGGER:\(duration(alarm.relativeOffset ?? 0))"
        }
        return [
            "BEGIN:VALARM",
            "ACTION:DISPLAY",
            field("DESCRIPTION", summary.isEmpty ? "Reminder" : summary),
            trigger,
            "END:VALARM"
        ]
    }

    private static func byDay(_ day: CalendarEvent.Recurrence.DayOfWeek) -> String {
        let weekdays = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"]
        let symbol = (1...7).contains(day.dayOfWeek) ? weekdays[day.dayOfWeek - 1] : "SU"
        return day.weekNumber == 0 ? symbol : "\(day.weekNumber)\(symbol)"
    }

    private static func frequency(_ frequency: CalendarEvent.Recurrence.Frequency) -> String {
        switch frequency {
        case .daily: "DAILY"
        case .weekly: "WEEKLY"
        case .monthly: "MONTHLY"
        case .yearly: "YEARLY"
        }
    }

    private static func duration(_ seconds: Int) -> String {
        let sign = seconds < 0 ? "-" : ""
        var remaining = abs(seconds)
        if remaining != 0, remaining % 604_800 == 0 { return "\(sign)P\(remaining / 604_800)W" }
        let days = remaining / 86_400
        remaining %= 86_400
        let hours = remaining / 3600
        remaining %= 3600
        let minutes = remaining / 60
        let secs = remaining % 60
        var result = "\(sign)P"
        if days > 0 { result += "\(days)D" }
        if hours > 0 || minutes > 0 || secs > 0 {
            result += "T"
            if hours > 0 { result += "\(hours)H" }
            if minutes > 0 { result += "\(minutes)M" }
            if secs > 0 { result += "\(secs)S" }
        }
        return result == "\(sign)P" ? "\(sign)PT0S" : result
    }

    private static func appendList(_ parts: inout [String], _ name: String, _ values: [Int]) {
        if !values.isEmpty { parts.append("\(name)=\(values.map(String.init).joined(separator: ","))") }
    }

    private static func appendIfPresent(_ lines: inout [String], _ name: String, _ value: String?) {
        if let value, !value.isEmpty { lines.append(field(name, value)) }
    }

    private static func field(_ name: String, _ value: String) -> String {
        "\(name):\(escape(value))"
    }

    private static func escape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: "\r\n", with: "\\n")
            .replacingOccurrences(of: "\n", with: "\\n")
    }

    private static func fold(_ line: String) -> String {
        let limit = 75
        var folded = ""
        var octets = 0
        for character in line {
            let width = String(character).utf8.count
            if octets + width > limit {
                folded += "\r\n "
                octets = 1
            }
            folded.append(character)
            octets += width
        }
        return folded
    }

    private static func utcDateTime(seconds: Int) -> String {
        utcDateTime(Date(timeIntervalSince1970: TimeInterval(seconds)))
    }

    private static func utcDateTime(_ instant: Date) -> String {
        formatter("yyyyMMdd'T'HHmmss'Z'", zone: TimeZone(identifier: "UTC") ?? .current).string(from: instant)
    }

    private static func date(_ seconds: Int, zone: TimeZone) -> String {
        formatter("yyyyMMdd", zone: zone).string(from: Date(timeIntervalSince1970: TimeInterval(seconds)))
    }

    private static func formatter(_ format: String, zone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = format
        return formatter
    }
}
