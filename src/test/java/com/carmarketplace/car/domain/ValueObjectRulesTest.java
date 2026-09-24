package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectRulesTest {

    @Nested
    class PriceRules {

        @Test
        void storesTndAmountsWithThreeDecimals() {
            Price price = Price.tnd(new BigDecimal("185000"), true);

            assertThat(price.amount()).isEqualTo(new BigDecimal("185000.000"));
            assertThat(price.currency()).isEqualTo("TND");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-0.001"})
        void rejectsNonPositiveAmounts(String amount) {
            assertThatThrownBy(() -> Price.tnd(new BigDecimal(amount), false))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("Price must be positive");
        }

        @Test
        void rejectsMoreThanThreeDecimals() {
            assertThatThrownBy(() -> Price.tnd(new BigDecimal("1500.0001"), false))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("A price in TND has at most 3 decimals");
        }

        @Test
        void rejectsOtherCurrencies() {
            assertThatThrownBy(() -> new Price(new BigDecimal("1500"), "EUR", false))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("Only prices in TND are supported");
        }
    }

    @Nested
    class EngineRules {

        @Test
        void electricCarHasNoCylindersOrDisplacement() {
            assertThatThrownBy(() -> new Engine(FuelType.ELECTRIC, Transmission.AUTOMATIC, 283, 10, 4, null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("An electric car has no cylinders or engine displacement");
        }

        @Test
        void electricCarWithoutCylindersIsValid() {
            assertThatNoException().isThrownBy(
                    () -> new Engine(FuelType.ELECTRIC, Transmission.AUTOMATIC, 283, 10, null, null));
        }
    }

    @Nested
    class HistoryRules {

        @Test
        void newCarCannotHaveHighMileage() {
            assertThatThrownBy(() -> new History(5_000, true, Condition.NEW, Origin.LOCAL, false, 0))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("A new car cannot have more than 100 km");
        }

        @Test
        void newCarCannotHavePreviousOwners() {
            assertThatThrownBy(() -> new History(10, true, Condition.NEW, Origin.IMPORTED, false, 1))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("A new car cannot have previous owners");
        }
    }
}
