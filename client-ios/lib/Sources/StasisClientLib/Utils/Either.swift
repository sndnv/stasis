public enum Either<L: Sendable, R: Sendable>: Sendable {
    case left(L)
    case right(R)

    public var isLeft: Bool {
        switch self {
        case .left: true
        case .right: false
        }
    }

    public var isRight: Bool {
        switch self {
        case .left: false
        case .right: true
        }
    }

    public var leftValue: L? {
        if case .left(let value) = self { value } else { nil }
    }

    public var rightValue: R? {
        if case .right(let value) = self { value } else { nil }
    }

    public func map<R2: Sendable>(_ transform: (R) -> R2) -> Either<L, R2> {
        switch self {
        case .left(let value): .left(value)
        case .right(let value): .right(transform(value))
        }
    }

    public func flatMap<R2: Sendable>(_ transform: (R) -> Either<L, R2>) -> Either<L, R2> {
        switch self {
        case .left(let value): .left(value)
        case .right(let value): transform(value)
        }
    }

    public func fold<T>(left onLeft: (L) -> T, right onRight: (R) -> T) -> T {
        switch self {
        case .left(let value): onLeft(value)
        case .right(let value): onRight(value)
        }
    }
}

extension Either: Equatable where L: Equatable, R: Equatable {}
extension Either: Hashable where L: Hashable, R: Hashable {}
