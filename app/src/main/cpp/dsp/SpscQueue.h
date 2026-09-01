#pragma once

#include <array>
#include <atomic>
#include <cstddef>
#include <type_traits>

namespace intervaltablet::dsp {

template <typename T, std::size_t Capacity>
class SpscQueue {
    static_assert(Capacity >= 2, "Capacity must be at least two");
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be a power of two");
    static_assert(std::is_trivially_copyable_v<T>, "Events must be trivially copyable");

public:
    static constexpr std::size_t usableCapacity() noexcept { return Capacity - 1U; }

    bool push(const T& value) noexcept {
        const auto write = writeIndex_.load(std::memory_order_relaxed);
        const auto next = (write + 1U) & mask;
        if (next == readIndex_.load(std::memory_order_acquire)) {
            return false;
        }
        storage_[write] = value;
        writeIndex_.store(next, std::memory_order_release);
        return true;
    }

    bool pop(T& value) noexcept {
        const auto read = readIndex_.load(std::memory_order_relaxed);
        if (read == writeIndex_.load(std::memory_order_acquire)) {
            return false;
        }
        value = storage_[read];
        readIndex_.store((read + 1U) & mask, std::memory_order_release);
        return true;
    }

    void clearFromConsumer() noexcept {
        readIndex_.store(writeIndex_.load(std::memory_order_acquire), std::memory_order_release);
    }

    std::size_t sizeApprox() const noexcept {
        const auto write = writeIndex_.load(std::memory_order_acquire);
        const auto read = readIndex_.load(std::memory_order_acquire);
        return (write - read) & mask;
    }

private:
    static constexpr std::size_t mask = Capacity - 1U;
    alignas(64) std::array<T, Capacity> storage_{};
    alignas(64) std::atomic<std::size_t> writeIndex_{0};
    alignas(64) std::atomic<std::size_t> readIndex_{0};
};

}  // namespace intervaltablet::dsp
