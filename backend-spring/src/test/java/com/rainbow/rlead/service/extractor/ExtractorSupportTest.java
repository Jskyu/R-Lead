package com.rainbow.rlead.service.extractor;

import com.rainbow.rlead.domain.model.ExtractedData;
import com.rainbow.rlead.domain.model.LeadStatus;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorSupportTest {

    @Test
    void parsesCodeBlockJsonAndNullStrings() throws Exception {
        String json = "```json\n{\"is_business_card\": true, \"name\": \"홍길동\", \"company\": \"테스트\", "
                + "\"department\": \"null\", \"email\": \"a@b.co\", \"phone\": null}\n```";
        ExtractedData data = ExtractorSupport.parse(json);
        assertEquals("홍길동", data.getName());
        assertEquals("테스트", data.getCompany());
        assertNull(data.getDepartment(), "문자열 'null' 은 빈 값으로 취급");
        assertNull(data.getPhone());
    }

    @Test
    void parseAllReturnsOneEntryPerCardInOrder() throws Exception {
        String json = "{\"cards\": [{\"name\": \"가\", \"company\": \"A사\"}, {\"name\": \"나\", \"company\": \"B사\"}, "
                + "{\"name\": null, \"company\": null}, {\"name\": \"다\", \"company\": \"C사\"}]}";
        java.util.List<ExtractedData> cards = ExtractorSupport.parseAll(json);
        assertEquals(3, cards.size(), "빈 항목은 건너뛴다");
        assertEquals("가", cards.get(0).getName());
        assertEquals("C사", cards.get(2).getCompany());
    }

    @Test
    void parseAllAcceptsLegacySingleObject() throws Exception {
        java.util.List<ExtractedData> cards = ExtractorSupport.parseAll("{\"name\": \"홍길동\", \"email\": \"a@b.co\"}");
        assertEquals(1, cards.size());
        assertEquals("홍길동", cards.get(0).getName());
    }

    @Test
    void parseAllRejectsEmptyCardsArray() {
        assertThrows(UnreadableCardException.class, () -> ExtractorSupport.parseAll("{\"cards\": []}"));
    }

    @Test
    void rejectsNonBusinessCard() {
        assertThrows(UnreadableCardException.class,
                () -> ExtractorSupport.parse("{\"is_business_card\": false, \"name\": \"지어낸 이름\"}"));
    }

    @Test
    void rejectsAllNullResult() {
        assertThrows(UnreadableCardException.class,
                () -> ExtractorSupport.parse("{\"is_business_card\": true, \"name\": null, \"company\": \"\"}"));
    }

    @Test
    void rejectsEmptyResponse() {
        assertThrows(RuntimeException.class, () -> ExtractorSupport.parse("  "));
    }

    @Test
    void detectsBlankImageButNotTextImage() throws Exception {
        BufferedImage white = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = white.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 200);
        assertTrue(ExtractorSupport.isBlankImage(toJpeg(white)));

        g.setColor(Color.BLACK);
        g.drawString("Hong Gil-dong  010-1234-5678", 20, 100);
        g.fillRect(20, 120, 200, 10);
        g.dispose();
        assertFalse(ExtractorSupport.isBlankImage(toJpeg(white)));
    }

    @Test
    void leadStatusIsCaseInsensitiveAndSerializesLowercase() {
        assertEquals(LeadStatus.EXTRACTED, LeadStatus.fromValue("extracted"));
        assertEquals(LeadStatus.CRM_REGISTERED, LeadStatus.fromValue("CRM_REGISTERED"));
        assertEquals("approved", LeadStatus.APPROVED.toValue());
        assertThrows(IllegalArgumentException.class, () -> LeadStatus.fromValue("bogus"));
    }

    private static byte[] toJpeg(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
