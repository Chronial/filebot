
package net.sourceforge.filebot.web;


import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sublight.webservice.Subtitle;


public class SublightSubtitleClientTest {
	
	private static SublightSubtitleClient client = new SublightSubtitleClient();
	
	
	@BeforeClass
	public static void login() {
		client.setClient("SublightCmd", "12c72276-b95f-4144-bb2a-879775c71437");
		client.setUser("filebot-test", "correcthorsebatterystaple");
		client.login();
	}
	
	
	@Test
	public void search() {
		List<SearchResult> list = client.search("babylon 5");
		
		Movie sample = (Movie) list.get(0);
		
		// check sample entry
		assertEquals("Babylon 5", sample.getName());
		assertEquals(105946, sample.getImdbId());
		
		// check size
		assertEquals(8, list.size());
	}
	
	
	@Test
	public void getSubtitleListEnglish() {
		List<SubtitleDescriptor> list = client.getSubtitleList(new Movie("Heroes", 2006, 813715), "English");
		
		SubtitleDescriptor sample = list.get(0);
		assertEquals("English", sample.getLanguageName());
		
		// check size
		assertTrue(list.size() > 45);
	}
	
	
	@Test
	public void getSubtitleListAllLanguages() {
		List<SubtitleDescriptor> list = client.getSubtitleList(new Movie("Terminator 2", 1991, 103064), "Croatian");
		
		SubtitleDescriptor sample = list.get(0);
		
		assertEquals("Terminator.2-Judgment.Day[1991]DvDrip-aXXo", sample.getName());
		assertEquals("Croatian", sample.getLanguageName());
	}
	
	
	@Test
	public void getSubtitleListVideoHash() throws Exception {
		List<Subtitle> list = client.getSubtitleList("001c6e0000320458004ee6f6859e5b7844767d44336e5624edbb", null, null, "English");
		
		Subtitle sample = list.get(0);
		assertEquals("Jurassic Park", sample.getTitle());
		assertEquals("Jurassic.Park[1993]DvDrip-aXXo", sample.getRelease());
		assertEquals(true, sample.isIsLinked());
	}
	
	
	@Test
	public void getZipArchive() throws Exception {
		Subtitle subtitle = new Subtitle();
		subtitle.setSubtitleID("1b4e9868-dded-49d0-b6e2-2d145328f6d4");
		
		byte[] zip = client.getZipArchive(subtitle);
		
		// read first zip entry
		ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zip));
		
		try {
			ZipEntry entry = zipInputStream.getNextEntry();
			
			assertEquals("Terminator The Sarah Connor Chronicles.srt", entry.getName());
			assertEquals(38959, entry.getSize(), 0);
		} finally {
			zipInputStream.close();
		}
	}
	
	
	@AfterClass
	public static void logout() {
		// logout manually
		client.logout();
	}
	
}
