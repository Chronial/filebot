package net.filebot.format;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.format.Define.*;
import static net.filebot.format.ExpressionFormatMethods.*;
import static net.filebot.hash.VerificationUtilities.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.web.EpisodeFormat.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.filebot.Cache;
import net.filebot.Language;
import net.filebot.MediaTypes;
import net.filebot.Settings;
import net.filebot.WebServices;
import net.filebot.hash.HashType;
import net.filebot.media.MetaAttributes;
import net.filebot.mediainfo.MediaInfo;
import net.filebot.mediainfo.MediaInfo.StreamKind;
import net.filebot.similarity.SimilarityComparator;
import net.filebot.util.FileUtilities;
import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.web.AnidbSearchResult;
import net.filebot.web.AudioTrack;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.Movie;
import net.filebot.web.MoviePart;
import net.filebot.web.MultiEpisode;
import net.filebot.web.SearchResult;
import net.filebot.web.SimpleDate;
import net.filebot.web.TheTVDBSearchResult;

import com.cedarsoftware.util.io.JsonWriter;

public class MediaBindingBean {

	private final Object infoObject;
	private final File mediaFile;
	private final Map<File, Object> context;

	private MediaInfo mediaInfo;
	private Object metaInfo;

	public MediaBindingBean(Object infoObject, File mediaFile) {
		this(infoObject, mediaFile, singletonMap(mediaFile, infoObject));
	}

	public MediaBindingBean(Object infoObject, File mediaFile, Map<File, Object> context) {
		this.infoObject = infoObject;
		this.mediaFile = mediaFile;
		this.context = context;
	}

	@Define(undefined)
	public <T> T undefined(String name) {
		// omit expressions that depend on undefined values
		throw new BindingException(name, "undefined");
	}

	@Define("n")
	public String getName() {
		if (infoObject instanceof Episode)
			return getEpisode().getSeriesName();
		if (infoObject instanceof Movie)
			return getMovie().getName();
		if (infoObject instanceof AudioTrack)
			return getAlbumArtist() != null ? getAlbumArtist() : getArtist();
		if (infoObject instanceof File)
			return FileUtilities.getName((File) infoObject);

		return null;
	}

	@Define("y")
	public Integer getYear() {
		if (infoObject instanceof Episode)
			return getEpisode().getSeriesStartDate().getYear();
		if (infoObject instanceof Movie)
			return getMovie().getYear();
		if (infoObject instanceof AudioTrack)
			return getReleaseDate() != null ? ((SimpleDate) getReleaseDate()).getYear() : new Scanner(getMediaInfo(StreamKind.General, 0, "Recorded_Date")).useDelimiter("\\D+").nextInt();

		return null;
	}

	@Define("s")
	public Integer getSeasonNumber() {
		return getEpisode().getSeason();
	}

	@Define("e")
	public Integer getEpisodeNumber() {
		return getEpisode().getEpisode();
	}

	@Define("es")
	public List<Integer> getEpisodeNumbers() {
		List<Integer> n = new ArrayList<Integer>();
		for (Episode it : getEpisodes()) {
			n.add(it.getEpisode());
		}
		return n;
	}

	@Define("sxe")
	public String getSxE() {
		return SeasonEpisode.formatSxE(getEpisode());
	}

	@Define("s00e00")
	public String getS00E00() {
		return SeasonEpisode.formatS00E00(getEpisode());
	}

	@Define("t")
	public String getTitle() {
		if (infoObject instanceof AudioTrack) {
			return getMusic().getTrackTitle() != null ? getMusic().getTrackTitle() : getMusic().getTitle();
		}

		// enforce title length limit by default
		int limit = 150;

		// single episode format
		if (getEpisodes().size() == 1) {
			return truncateText(getEpisode().getTitle(), limit);
		}

		// multi-episode format
		Set<String> title = new LinkedHashSet<String>();
		for (Episode it : getEpisodes()) {
			title.add(removeTrailingBrackets(it.getTitle()));
		}
		return truncateText(join(title, " & "), limit);
	}

	@Define("d")
	public SimpleDate getReleaseDate() {
		if (infoObject instanceof Episode) {
			return getEpisode().getAirdate();
		}
		if (infoObject instanceof Movie) {
			return (SimpleDate) getMetaInfo().getProperty("released");
		}
		if (infoObject instanceof AudioTrack) {
			return getMusic().getAlbumReleaseDate();
		}

		// no date info for the model
		return null;
	}

	@Define("airdate")
	public SimpleDate airdate() {
		return getEpisode().getAirdate();
	}

	@Define("age")
	public Number getAgeInDays() {
		return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - getReleaseDate().getTimeStamp());
	}

	@Define("startdate")
	public SimpleDate startdate() {
		return getEpisode().getSeriesStartDate();
	}

	@Define("absolute")
	public Integer getAbsoluteEpisodeNumber() {
		return getEpisode().getAbsolute();
	}

	@Define("special")
	public Integer getSpecialNumber() {
		return getEpisode().getSpecial();
	}

	@Define("series")
	public SearchResult getSeriesObject() {
		return getEpisode().getSeries();
	}

	@Define("alias")
	public List<String> getAliasNames() {
		if (infoObject instanceof Movie) {
			return asList(getMovie().getAliasNames());
		}

		if (infoObject instanceof Episode) {
			return asList(getSeriesObject().getAliasNames());
		}

		return null;
	}

	@Define("primaryTitle")
	public String getPrimaryTitle() throws Exception {
		if (infoObject instanceof Movie) {
			return WebServices.TheMovieDB.getMovieInfo(getMovie(), Locale.ENGLISH, false).getOriginalName();
		}

		if (infoObject instanceof Episode) {
			if (getSeriesObject() instanceof TheTVDBSearchResult) {
				return WebServices.TheTVDB.getSeriesInfo((TheTVDBSearchResult) getSeriesObject(), Locale.ENGLISH).getName();
			}
			if (getSeriesObject() instanceof AnidbSearchResult) {
				return ((AnidbSearchResult) getSeriesObject()).getPrimaryTitle();
			}
			return getSeriesObject().getName(); // default to original search result
		}

		return null;
	}

	@Define("tmdbid")
	public String getTmdbId() throws Exception {
		int tmdbid = getMovie().getTmdbId();

		if (tmdbid <= 0) {
			if (getMovie().getImdbId() <= 0) {
				return null;
			}

			// lookup IMDbID for TMDbID
			tmdbid = WebServices.TheMovieDB.getMovieInfo(getMovie(), Locale.ENGLISH, false).getId();
		}

		return String.valueOf(tmdbid);
	}

	@Define("imdbid")
	public String getImdbId() throws Exception {
		int imdbid = getMovie().getImdbId();

		if (imdbid <= 0) {
			if (getMovie().getTmdbId() <= 0) {
				return null;
			}

			// lookup IMDbID for TMDbID
			imdbid = WebServices.TheMovieDB.getMovieInfo(getMovie(), Locale.ENGLISH, false).getImdbId();
		}

		return String.format("tt%07d", imdbid);
	}

	@Define("vc")
	public String getVideoCodec() {
		// e.g. XviD, x264, DivX 5, MPEG-4 Visual, AVC, etc.
		String codec = getMediaInfo(StreamKind.Video, 0, "Encoded_Library/Name", "CodecID/Hint", "Format");

		// get first token (e.g. DivX 5 => DivX)
		return new Scanner(codec).next();
	}

	@Define("ac")
	public String getAudioCodec() {
		// e.g. AC-3, DTS, AAC, Vorbis, MP3, etc.
		String codec = getMediaInfo(StreamKind.Audio, 0, "CodecID/Hint", "Format");

		// remove punctuation (e.g. AC-3 => AC3)
		return codec.replaceAll("\\p{Punct}", "");
	}

	@Define("cf")
	public String getContainerFormat() {
		// container format extensions (e.g. avi, mkv mka mks, OGG, etc.)
		String extensions = getMediaInfo(StreamKind.General, 0, "Codec/Extensions", "Format");

		// get first extension
		return new Scanner(extensions).next().toLowerCase();
	}

	@Define("vf")
	public String getVideoFormat() {
		int width = Integer.parseInt(getMediaInfo(StreamKind.Video, 0, "Width"));
		int height = Integer.parseInt(getMediaInfo(StreamKind.Video, 0, "Height"));

		int ns = 0;
		int[] ws = new int[] { 15360, 7680, 3840, 1920, 1280, 1024, 854, 852, 720, 688, 512, 320 };
		int[] hs = new int[] { 8640, 4320, 2160, 1080, 720, 576, 576, 480, 480, 360, 240, 240 };
		for (int i = 0; i < ws.length - 1; i++) {
			if ((width >= ws[i] || height >= hs[i]) || (width > ws[i + 1] && height > hs[i + 1])) {
				ns = hs[i];
				break;
			}
		}
		if (ns > 0) {
			// e.g. 720p, nobody actually wants files to be tagged as interlaced, e.g. 720i
			return String.format("%dp", ns);
		}
		return null; // video too small
	}

	@Define("hpi")
	public String getExactVideoFormat() {
		String height = getMediaInfo(StreamKind.Video, 0, "Height");
		String scanType = getMediaInfo(StreamKind.Video, 0, "ScanType");

		// e.g. 720p
		return height + Character.toLowerCase(scanType.charAt(0));
	}

	@Define("af")
	public String getAudioChannels() {
		String channels = getMediaInfo(StreamKind.Audio, 0, "Channel(s)_Original", "Channel(s)");

		// e.g. 6ch
		return channels + "ch";
	}

	@Define("resolution")
	public String getVideoResolution() {
		List<Integer> dim = getDimension();

		if (dim.contains(null))
			return null;

		// e.g. 1280x720
		return join(dim, "x");
	}

	@Define("ws")
	public String getWidescreen() {
		List<Integer> dim = getDimension();

		// width-to-height aspect ratio greater than 1.37:1
		return (float) dim.get(0) / dim.get(1) > 1.37f ? "ws" : null;
	}

	@Define("sdhd")
	public String getVideoDefinitionCategory() {
		List<Integer> dim = getDimension();

		// SD (less than 720 lines) or HD (more than 720 lines)
		return dim.get(0) >= 1280 || dim.get(1) >= 720 ? "HD" : "SD";
	}

	@Define("dim")
	public List<Integer> getDimension() {
		String width = getMediaInfo(StreamKind.Video, 0, "Width");
		String height = getMediaInfo(StreamKind.Video, 0, "Height");

		return asList(Integer.parseInt(width), Integer.parseInt(height));
	}

	@Define("original")
	public String getOriginalFileName() throws Exception {
		return getOriginalFileName(mediaFile);
	}

	@Define("xattr")
	public Object getMetaAttributesObject() throws Exception {
		return new MetaAttributes(mediaFile).getObject();
	}

	@Define("crc32")
	public String getCRC32() throws IOException, InterruptedException {
		// use inferred media file
		File inferredMediaFile = getInferredMediaFile();

		// try to get checksum from file name
		for (String filename : new String[] { getOriginalFileName(inferredMediaFile), inferredMediaFile.getName() }) {
			if (filename != null) {
				String checksum = getEmbeddedChecksum(filename);
				if (checksum != null) {
					return checksum;
				}
			}
		}

		// try to get checksum from sfv file
		String checksum = getHashFromVerificationFile(inferredMediaFile, HashType.SFV, 3);
		if (checksum != null) {
			return checksum;
		}

		// calculate checksum from file
		return crc32(inferredMediaFile);
	}

	@Define("fn")
	public String getFileName() {
		// make sure media file is defined
		checkMediaFile();

		// file extension
		return FileUtilities.getName(mediaFile);
	}

	@Define("ext")
	public String getExtension() {
		// make sure media file is defined
		checkMediaFile();

		// file extension
		return FileUtilities.getExtension(mediaFile);
	}

	@Define("source")
	public String getVideoSource() {
		// use inferred media file
		File inferredMediaFile = getInferredMediaFile();

		// look for video source patterns in media file and it's parent folder
		return releaseInfo.getVideoSource(inferredMediaFile.getParent(), inferredMediaFile.getName(), getOriginalFileName(inferredMediaFile));
	}

	@Define("tags")
	public List<String> getVideoTags() {
		// use inferred media file
		File inferredMediaFile = getInferredMediaFile();

		// look for video source patterns in media file and it's parent folder
		List<String> matches = releaseInfo.getVideoTags(inferredMediaFile.getParent(), inferredMediaFile.getName(), getOriginalFileName(inferredMediaFile));
		if (matches.isEmpty()) {
			return null;
		}

		Set<String> tags = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		for (String m : matches) {
			// heavy normalization of whatever pattern was matched with the regex pattern
			tags.add(lowerTrail(upperInitial(normalizePunctuation(normalizeSpace(m, " ")))));
		}
		return new ArrayList<String>(tags);
	}

	@Define("group")
	public String getReleaseGroup() throws IOException {
		// use inferred media file
		File inferredMediaFile = getInferredMediaFile();

		// consider foldername, filename and original filename
		String[] filenames = new String[] { inferredMediaFile.getParentFile().getName(), inferredMediaFile.getName(), getOriginalFileName(inferredMediaFile) };

		// reduce false positives by removing the know titles from the name
		List<String> titles = new ArrayList<String>();
		titles.add(getName());
		titles.add(getYear().toString());
		titles.addAll(getAliasNames());
		if (infoObject instanceof Episode) {
			for (Episode it : getEpisodes()) {
				titles.add(it.getTitle());
			}
		}
		for (int i = 0; i < titles.size(); i++) {
			titles.set(i, normalizePunctuation(normalizeSpace(titles.get(i), " ")));
		}

		Pattern nonGroupPattern = releaseInfo.getCustomRemovePattern(titles);
		for (int i = 0; i < filenames.length; i++) {
			if (filenames[i] == null)
				continue;

			// normalize space characters
			filenames[i] = nonGroupPattern.matcher(normalizePunctuation(normalizeSpace(filenames[i], " "))).replaceAll("");
		}

		// look for release group names in media file and it's parent folder
		return releaseInfo.getReleaseGroup(filenames);
	}

	@Define("lang")
	public Language detectSubtitleLanguage() throws Exception {
		// make sure media file is defined
		checkMediaFile();

		Locale languageSuffix = releaseInfo.getLanguageSuffix(FileUtilities.getName(mediaFile));
		if (languageSuffix != null)
			return Language.getLanguage(languageSuffix);

		// require subtitle file
		if (!SUBTITLE_FILES.accept(mediaFile)) {
			return null;
		}

		// exclude VobSub from any normal text-based subtitle processing
		if (hasExtension(mediaFile, "idx")) {
			return Language.getLanguage(grepLanguageFromSUBIDX(mediaFile));
		} else if (hasExtension(mediaFile, "sub")) {
			for (File idx : getChildren(mediaFile.getParentFile(), new ExtensionFileFilter("idx"))) {
				if (isDerived(idx, mediaFile)) {
					return Language.getLanguage(grepLanguageFromSUBIDX(idx));
				}
			}
		}

		// try statistical language detection
		return Language.getLanguage(WebServices.OpenSubtitles.detectLanguage(readFile(mediaFile)));
	}

	@Define("actors")
	public Object getActors() {
		return getMetaInfo().getProperty("actors");
	}

	@Define("genres")
	public Object getGenres() {
		if (infoObject instanceof AudioTrack)
			return asList(getMediaInfo(StreamKind.General, 0, "Genre").split(";"));

		return getMetaInfo().getProperty("genres");
	}

	@Define("genre")
	public Object getPrimaryGenre() {
		return ((Iterable<?>) getGenres()).iterator().next();
	}

	@Define("director")
	public Object getDirector() {
		return getMetaInfo().getProperty("director");
	}

	@Define("certification")
	public Object getCertification() {
		return getMetaInfo().getProperty("certification");
	}

	@Define("rating")
	public Object getRating() {
		return getMetaInfo().getProperty("rating");
	}

	@Define("collection")
	public Object getCollection() {
		return getMetaInfo().getProperty("collection");
	}

	@Define("info")
	public synchronized AssociativeScriptObject getMetaInfo() {
		if (metaInfo == null) {
			try {
				if (infoObject instanceof Episode)
					metaInfo = WebServices.TheTVDB.getSeriesInfoByName(getEpisode().getSeriesName(), getEpisode().getLanguage() == null ? Locale.ENGLISH : getEpisode().getLanguage());
				if (infoObject instanceof Movie)
					metaInfo = WebServices.TheMovieDB.getMovieInfo(getMovie(), getMovie().getLanguage() == null ? Locale.ENGLISH : getMovie().getLanguage(), true);
			} catch (Exception e) {
				throw new RuntimeException("Failed to retrieve metadata: " + infoObject, e);
			}
		}
		return createMapBindings(new PropertyBindings(metaInfo, null));
	}

	@Define("imdb")
	public synchronized AssociativeScriptObject getImdbApiInfo() {
		Object data = null;

		try {
			if (infoObject instanceof Episode) {
				data = WebServices.OMDb.getMovieInfo(new Movie(getEpisode().getSeriesName(), getEpisode().getSeriesStartDate().getYear(), -1, -1));
			}
			if (infoObject instanceof Movie) {
				Movie m = getMovie();
				data = WebServices.OMDb.getMovieInfo(m.getImdbId() > 0 ? m : new Movie(null, -1, WebServices.TheMovieDB.getMovieInfo(getMovie(), Locale.ENGLISH, false).getImdbId(), -1));
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to retrieve metadata: " + infoObject, e);
		}

		return createMapBindings(new PropertyBindings(data, null));
	}

	@Define("episodelist")
	public Object getEpisodeList() throws Exception {
		return ((EpisodeListProvider) getDatabase()).getEpisodeList(getSeriesObject(), getEpisode().getOrder(), getEpisode().getLanguage());
	}

	@Define("database")
	public Object getDatabase() {
		if (infoObject instanceof Episode) {
			return WebServices.getServiceBySearchResult(getSeriesObject());
		}
		if (infoObject instanceof Movie) {
			return WebServices.getServiceBySearchResult(getMovie());
		}
		if (infoObject instanceof AudioTrack) {
			return WebServices.getServiceBySearchResult(getMusic());
		}
		return null;
	}

	@Define("bitrate")
	public Float getBitRate() {
		return new Float(getMediaInfo(StreamKind.General, 0, "OverallBitRate"));
	}

	@Define("duration")
	public Float getDuration() {
		return new Float(getMediaInfo(StreamKind.General, 0, "Duration"));
	}

	@Define("seconds")
	public Integer getSeconds() {
		return Math.round(getDuration() / 1000f);
	}

	@Define("minutes")
	public Integer getDurationInMinutes() {
		return Math.round(getDuration() / 60000f);
	}

	@Define("media")
	public AssociativeScriptObject getGeneralMediaInfo() {
		return createMapBindings(getMediaInfo().snapshot(StreamKind.General, 0));
	}

	@Define("video")
	public AssociativeScriptObject getVideoInfo() {
		return createMapBindings(getMediaInfo().snapshot(StreamKind.Video, 0));
	}

	@Define("audio")
	public AssociativeScriptObject getAudioInfo() {
		return createMapBindings(getMediaInfo().snapshot(StreamKind.Audio, 0));
	}

	@Define("text")
	public AssociativeScriptObject getTextInfo() {
		return createMapBindings(getMediaInfo().snapshot(StreamKind.Text, 0));
	}

	@Define("videos")
	public List<AssociativeScriptObject> getVideoInfoList() {
		return createMapBindingsList(getMediaInfo().snapshot().get(StreamKind.Video));
	}

	@Define("audios")
	public List<AssociativeScriptObject> getAudioInfoList() {
		return createMapBindingsList(getMediaInfo().snapshot().get(StreamKind.Audio));
	}

	@Define("texts")
	public List<AssociativeScriptObject> getTextInfoList() {
		return createMapBindingsList(getMediaInfo().snapshot().get(StreamKind.Text));
	}

	@Define("artist")
	public String getArtist() {
		return getMusic().getArtist();
	}

	@Define("albumArtist")
	public String getAlbumArtist() {
		return getMusic().getAlbumArtist();
	}

	@Define("album")
	public String getAlbum() {
		return getMusic().getAlbum();
	}

	@Define("episode")
	public Episode getEpisode() {
		return (Episode) infoObject;
	}

	@Define("episodes")
	public List<Episode> getEpisodes() {
		return infoObject instanceof MultiEpisode ? ((MultiEpisode) infoObject).getEpisodes() : asList(getEpisode());
	}

	@Define("movie")
	public Movie getMovie() {
		return (Movie) infoObject;
	}

	@Define("music")
	public AudioTrack getMusic() {
		return (AudioTrack) infoObject;
	}

	@Define("pi")
	public Integer getPart() {
		if (infoObject instanceof AudioTrack)
			return getMusic().getTrack() != null ? getMusic().getTrack() : Integer.parseInt(getMediaInfo(StreamKind.General, 0, "Track/Position"));
		if (infoObject instanceof MoviePart)
			return ((MoviePart) infoObject).getPartIndex();

		return null;
	}

	@Define("pn")
	public Integer getPartCount() {
		if (infoObject instanceof AudioTrack)
			return getMusic().getTrackCount() != null ? getMusic().getTrackCount() : Integer.parseInt(getMediaInfo(StreamKind.General, 0, "Track/Position_Total"));
		if (infoObject instanceof MoviePart)
			return ((MoviePart) infoObject).getPartCount();

		return null;
	}

	@Define("mediaType")
	public List<String> getMediaType() throws Exception {
		return asList(MediaTypes.getDefault().getMediaType(getExtension()).split("/")); // format engine does not allow / in binding value
	}

	@Define("file")
	public File getMediaFile() {
		return mediaFile;
	}

	@Define("folder")
	public File getMediaParentFolder() {
		return getMediaFile().getParentFile();
	}

	@Define("home")
	public File getUserHome() throws IOException {
		return new File(System.getProperty("user.home"));
	}

	@Define("now")
	public Date getNow() {
		return new Date();
	}

	@Define("defines")
	public Map<String, String> getUserDefinedArguments() throws IOException {
		return Settings.getApplicationArguments().defines;
	}

	@Define("label")
	public String getUserDefinedLabel() throws IOException {
		for (Entry<String, String> it : getUserDefinedArguments().entrySet()) {
			if (it.getKey().endsWith("label")) {
				return it.getValue();
			}
		}
		return null;
	}

	@Define("object")
	public Object getInfoObject() {
		return infoObject;
	}

	@Define("i")
	public Integer getModelIndex() {
		return 1 + identityIndexOf(context.values(), getInfoObject());
	}

	@Define("di")
	public Integer getDuplicateIndex() {
		List<Object> duplicates = new ArrayList<Object>();
		for (Object it : context.values()) {
			if (getInfoObject().equals(it)) {
				duplicates.add(it);
			}
		}
		int di = identityIndexOf(duplicates, getInfoObject());
		return di == 0 ? null : di;
	}

	@Define("self")
	public AssociativeScriptObject getSelf() {
		return createBindingObject(mediaFile, infoObject, context);
	}

	@Define("model")
	public List<AssociativeScriptObject> getModel() {
		List<AssociativeScriptObject> result = new ArrayList<AssociativeScriptObject>();
		for (Entry<File, Object> it : context.entrySet()) {
			result.add(createBindingObject(it.getKey(), it.getValue(), context));
		}
		return result;
	}

	@Define("json")
	public String getInfoObjectDump() throws Exception {
		return JsonWriter.objectToJson(infoObject);
	}

	public File getInferredMediaFile() {
		// make sure media file is defined
		checkMediaFile();

		if (mediaFile.isDirectory()) {
			// just select the first video file in the folder as media sample
			SortedSet<File> videos = new TreeSet<File>(filter(listFiles(mediaFile), VIDEO_FILES));
			if (videos.size() > 0) {
				return videos.iterator().next();
			}
		} else if (SUBTITLE_FILES.accept(mediaFile) || ((infoObject instanceof Episode || infoObject instanceof Movie) && !VIDEO_FILES.accept(mediaFile))) {
			// prefer equal match from current context if possible
			if (context != null) {
				for (Entry<File, Object> it : context.entrySet()) {
					if (infoObject.equals(it.getValue()) && VIDEO_FILES.accept(it.getKey())) {
						return it.getKey();
					}
				}
			}

			// file is a subtitle, or nfo, etc
			String baseName = stripReleaseInfo(FileUtilities.getName(mediaFile)).toLowerCase();
			List<File> videos = getChildren(mediaFile.getParentFile(), VIDEO_FILES);

			// find corresponding movie file
			for (File movieFile : videos) {
				if (!baseName.isEmpty() && stripReleaseInfo(FileUtilities.getName(movieFile)).toLowerCase().startsWith(baseName)) {
					return movieFile;
				}
			}

			// still no good match found -> just take the most probable video from the same folder
			if (videos.size() > 0) {
				sort(videos, new SimilarityComparator(FileUtilities.getName(mediaFile)) {

					@Override
					public int compare(Object o1, Object o2) {
						return super.compare(FileUtilities.getName((File) o1), FileUtilities.getName((File) o2));
					}
				});
				return videos.get(0);
			}
		}

		return mediaFile;
	}

	private void checkMediaFile() throws RuntimeException {
		// make sure file is not null, and that it is an existing file
		if (mediaFile == null) {
			throw new RuntimeException("Path to media file has not been set");
		}
	}

	private synchronized MediaInfo getMediaInfo() {
		if (mediaInfo == null) {
			// make sure media file is defined
			checkMediaFile();

			MediaInfo newMediaInfo = new MediaInfo();

			// use inferred media file (e.g. actual movie file instead of subtitle file)
			if (!newMediaInfo.open(getInferredMediaFile())) {
				throw new RuntimeException("Cannot open media file: " + mediaFile);
			}

			mediaInfo = newMediaInfo;
		}

		return mediaInfo;
	}

	private Integer identityIndexOf(Iterable<?> c, Object o) {
		Iterator<?> itr = c.iterator();
		for (int i = 0; itr.hasNext(); i++) {
			Object next = itr.next();
			if (o == next)
				return i;
		}
		return null;
	}

	private String getMediaInfo(StreamKind streamKind, int streamNumber, String... keys) {
		for (String key : keys) {
			String value = getMediaInfo().get(streamKind, streamNumber, key);
			if (value.length() > 0) {
				return value;
			}
		}
		return undefined(String.format("%s[%d][%s]", streamKind, streamNumber, join(keys, ", ")));
	}

	private AssociativeScriptObject createBindingObject(File file, Object info, Map<File, Object> context) {
		MediaBindingBean mediaBindingBean = new MediaBindingBean(info, file, context) {
			@Define(undefined)
			public <T> T undefined(String name) {
				return null; // never throw exceptions for empty or null values
			}
		};
		return new AssociativeScriptObject(new ExpressionBindings(mediaBindingBean));
	}

	private AssociativeScriptObject createMapBindings(Map<?, ?> map) {
		return new AssociativeScriptObject(map) {

			@Override
			public Object getProperty(String name) {
				Object value = super.getProperty(name);

				if (value == null) {
					undefined(name);
				}

				// auto-clean value of path separators
				if (value instanceof CharSequence) {
					return replacePathSeparators(value.toString()).trim();
				}

				return value;
			}
		};
	}

	private List<AssociativeScriptObject> createMapBindingsList(List<Map<String, String>> mapList) {
		List<AssociativeScriptObject> bindings = new ArrayList<AssociativeScriptObject>();
		for (Map<?, ?> it : mapList) {
			bindings.add(createMapBindings(it));
		}
		return bindings;
	}

	private String crc32(File file) throws IOException, InterruptedException {
		// try to get checksum from cache
		Cache cache = Cache.getCache("checksum");

		String hash = cache.get(file, String.class);
		if (hash != null) {
			return hash;
		}

		// compute and cache checksum
		hash = computeHash(file, HashType.SFV);
		cache.put(file, hash);
		return hash;
	}

	private Locale grepLanguageFromSUBIDX(File idx) throws IOException {
		String text = new String(readFile(idx), "UTF-8");

		// # English
		// id: en, index: 0
		Pattern pattern = Pattern.compile("^id: (\\w+), index: (\\d+)", Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(text);

		if (matcher.find()) {
			return new Locale(matcher.group(1));
		} else {
			return null;
		}
	}

	private String getOriginalFileName(File file) {
		try {
			return new MetaAttributes(file).getOriginalName();
		} catch (Throwable e) {
			return null;
		}
	}

	private String truncateText(String title, int limit) {
		if (title.length() < limit) {
			return title;
		}

		String[] words = title.split("\\s+");
		StringBuilder s = new StringBuilder();

		for (int i = 0; i < words.length && s.length() + words[i].length() < limit; i++) {
			if (i > 0) {
				s.append(' ');
			}
			s.append(words[i]);
		}

		return s.toString().trim();
	}

	@Override
	public String toString() {
		return String.format("%s ⇔ %s", infoObject, mediaFile == null ? null : mediaFile.getName());
	}
}
