package com.simulacrum.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NmeaParserTest {

    @Test
    void parsesGgaSentence() {
        String gga = "$GPGGA,172814.0,3723.46587704,N,12202.26957864,W,2,6,1.2,18.893,M,-25.669,M,2.0,0031*4F";
        DataSource.Fix fix = NmeaParser.parse(gga);
        assertNotNull(fix);
        assertEquals(37.39109795, fix.latitudeDeg(), 1e-6);
        assertEquals(-122.0378263107, fix.longitudeDeg(), 1e-6);
        assertEquals(18.893, fix.altitudeM(), 1e-6);
    }

    @Test
    void parsesRmcSentence() {
        String rmc = "$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A";
        DataSource.Fix fix = NmeaParser.parse(rmc);
        assertNotNull(fix);
        assertEquals(48.1173, fix.latitudeDeg(), 1e-4);
        assertEquals(11.5166, fix.longitudeDeg(), 1e-4);
        assertEquals(84.4, fix.headingDeg(), 1e-6);
    }

    @Test
    void rejectsBadChecksum() {
        // Change the checksum byte at the end
        String gga = "$GPGGA,172814.0,3723.46587704,N,12202.26957864,W,2,6,1.2,18.893,M,-25.669,M,2.0,0031*00";
        assertNull(NmeaParser.parse(gga));
    }

    @Test
    void rejectsBlankAndNull() {
        assertNull(NmeaParser.parse(null));
        assertNull(NmeaParser.parse(""));
        assertNull(NmeaParser.parse("not-a-nmea-sentence"));
    }

    @Test
    void ignoresUnsupportedSentence() {
        assertNull(NmeaParser.parse("$GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39"));
    }
}
