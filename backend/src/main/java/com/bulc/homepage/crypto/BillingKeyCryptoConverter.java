package com.bulc.homepage.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * billing_key 컬럼 at-rest 암복호화 컨버터.
 * 저장 시 {@link BillingKeyCipher#encrypt}, 로드 시 {@link BillingKeyCipher#decrypt}.
 *
 * JPA가 컨버터를 인스턴스화하므로 Spring 빈 주입 대신 static 인스턴스를 사용한다.
 * (cipher가 아직 준비 전이면 원문 그대로 — 정상 런타임에는 발생하지 않음)
 */
@Converter
public class BillingKeyCryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        BillingKeyCipher cipher = BillingKeyCipher.getInstance();
        return cipher == null ? attribute : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        BillingKeyCipher cipher = BillingKeyCipher.getInstance();
        return cipher == null ? dbData : cipher.decrypt(dbData);
    }
}
