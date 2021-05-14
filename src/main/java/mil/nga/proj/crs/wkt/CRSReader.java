package mil.nga.proj.crs.wkt;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.nga.proj.ProjectionException;
import mil.nga.proj.crs.CoordinateReferenceSystem;
import mil.nga.proj.crs.CoordinateReferenceSystemType;
import mil.nga.proj.crs.common.Axis;
import mil.nga.proj.crs.common.AxisDirectionType;
import mil.nga.proj.crs.common.CoordinateSystem;
import mil.nga.proj.crs.common.CoordinateSystemType;
import mil.nga.proj.crs.common.DatumEnsemble;
import mil.nga.proj.crs.common.DatumEnsembleMember;
import mil.nga.proj.crs.common.Dynamic;
import mil.nga.proj.crs.common.Extent;
import mil.nga.proj.crs.common.GeographicBoundingBox;
import mil.nga.proj.crs.common.Identifier;
import mil.nga.proj.crs.common.ReferenceFrame;
import mil.nga.proj.crs.common.TemporalExtent;
import mil.nga.proj.crs.common.Unit;
import mil.nga.proj.crs.common.UnitType;
import mil.nga.proj.crs.common.Usage;
import mil.nga.proj.crs.common.VerticalExtent;
import mil.nga.proj.crs.derived.DerivedCoordinateReferenceSystem;
import mil.nga.proj.crs.derived.DerivingConversion;
import mil.nga.proj.crs.engineering.EngineeringCoordinateReferenceSystem;
import mil.nga.proj.crs.engineering.EngineeringDatum;
import mil.nga.proj.crs.geo.Ellipsoid;
import mil.nga.proj.crs.geo.GeoCoordinateReferenceSystem;
import mil.nga.proj.crs.geo.GeoDatumEnsemble;
import mil.nga.proj.crs.geo.GeoReferenceFrame;
import mil.nga.proj.crs.geo.PrimeMeridian;
import mil.nga.proj.crs.operation.OperationMethod;
import mil.nga.proj.crs.operation.OperationParameter;
import mil.nga.proj.crs.operation.OperationParameterFile;
import mil.nga.proj.crs.operation.Parameter;
import mil.nga.proj.crs.parametric.ParametricCoordinateReferenceSystem;
import mil.nga.proj.crs.parametric.ParametricDatum;
import mil.nga.proj.crs.projected.MapProjection;
import mil.nga.proj.crs.projected.ProjectedCoordinateReferenceSystem;
import mil.nga.proj.crs.temporal.TemporalCoordinateReferenceSystem;
import mil.nga.proj.crs.temporal.TemporalDatum;
import mil.nga.proj.crs.vertical.VerticalCoordinateReferenceSystem;
import mil.nga.proj.crs.vertical.VerticalDatumEnsemble;
import mil.nga.proj.crs.vertical.VerticalReferenceFrame;

/**
 * Well-Known Text reader
 * 
 * @author osbornb
 */
public class CRSReader implements Closeable {

	/**
	 * Logger
	 */
	private static final Logger logger = Logger
			.getLogger(CRSReader.class.getName());

	/**
	 * Axis Name and Abbreviation Pattern
	 */
	private static final String AXIS_NAME_ABBREV_PATTERN = "((.+ )|^)\\([a-zA-Z]+\\)$";

	/**
	 * Read a Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CoordinateReferenceSystem read(String text)
			throws IOException {
		return read(text, false);
	}

	/**
	 * Read a Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @param strict
	 *            strict enforcement
	 * @return Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CoordinateReferenceSystem read(String text, boolean strict)
			throws IOException {
		CoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text, strict);
		try {
			crs = reader.read();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @param expected
	 *            expected types
	 * @return Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CoordinateReferenceSystem read(String text,
			CoordinateReferenceSystemType... expected) throws IOException {
		return read(text, false, expected);
	}

	/**
	 * Read a Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @param strict
	 *            strict enforcement
	 * @param expected
	 *            expected types
	 * @return Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CoordinateReferenceSystem read(String text, boolean strict,
			CoordinateReferenceSystemType... expected) throws IOException {
		CoordinateReferenceSystem crs = read(text, strict);
		Set<CoordinateReferenceSystemType> expectedSet = new HashSet<>(
				Arrays.asList(expected));
		if (!expectedSet.contains(crs.getType())) {
			throw new ProjectionException(
					"Unexpected Coordinate Reference System Type: "
							+ crs.getType() + ", Expected: " + expectedSet);
		}
		return crs;
	}

	/**
	 * Read a Geodetic or Geographic Coordinate Reference System from the
	 * well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Geodetic or Geographic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeoCoordinateReferenceSystem readGeo(String text)
			throws IOException {
		return (GeoCoordinateReferenceSystem) read(text,
				CoordinateReferenceSystemType.GEODETIC,
				CoordinateReferenceSystemType.GEOGRAPHIC);
	}

	/**
	 * Read a Geodetic Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Geodetic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeoCoordinateReferenceSystem readGeodetic(String text)
			throws IOException {
		return (GeoCoordinateReferenceSystem) read(text,
				CoordinateReferenceSystemType.GEODETIC);
	}

	/**
	 * Read a Geographic Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Geographic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeoCoordinateReferenceSystem readGeographic(String text)
			throws IOException {
		return (GeoCoordinateReferenceSystem) read(text,
				CoordinateReferenceSystemType.GEOGRAPHIC);
	}

	/**
	 * Read a Projected Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Projected Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ProjectedCoordinateReferenceSystem readProjected(String text)
			throws IOException {
		ProjectedCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readProjected();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Projected Geodetic Coordinate Reference System from the well-known
	 * text
	 * 
	 * @param text
	 *            well-known text
	 * @return Projected Geodetic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ProjectedCoordinateReferenceSystem readProjectedGeodetic(
			String text) throws IOException {
		ProjectedCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readProjectedGeodetic();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Projected Geographic Coordinate Reference System from the
	 * well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Projected Geographic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ProjectedCoordinateReferenceSystem readProjectedGeographic(
			String text) throws IOException {
		ProjectedCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readProjectedGeographic();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Vertical Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Vertical Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static VerticalCoordinateReferenceSystem readVertical(String text)
			throws IOException {
		return (VerticalCoordinateReferenceSystem) read(text,
				CoordinateReferenceSystemType.VERTICAL);
	}

	/**
	 * Read an Engineering Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Engineering Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static EngineeringCoordinateReferenceSystem readEngineering(
			String text) throws IOException {
		EngineeringCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readEngineering();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read an Parametric Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Parametric Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ParametricCoordinateReferenceSystem readParametric(
			String text) throws IOException {
		ParametricCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readParametric();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read an Temporal Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Temporal Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static TemporalCoordinateReferenceSystem readTemporal(String text)
			throws IOException {
		TemporalCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readTemporal();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Derived Coordinate Reference System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Derived Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static DerivedCoordinateReferenceSystem readDerived(String text)
			throws IOException {
		return (DerivedCoordinateReferenceSystem) read(text,
				CoordinateReferenceSystemType.DERIVED);
	}

	/**
	 * Read a Backward Compatible Geodetic or Geographic Coordinate Reference
	 * System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Geodetic or Geographic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeoCoordinateReferenceSystem readGeoCompat(String text)
			throws IOException {
		GeoCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readGeoCompat();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Backward Compatible Geodetic Coordinate Reference System from the
	 * well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Geodetic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeoCoordinateReferenceSystem readGeodeticCompat(String text)
			throws IOException {
		GeoCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readGeodeticCompat();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Backward Compatible Geographic Coordinate Reference System from
	 * the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Geographic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeoCoordinateReferenceSystem readGeographicCompat(String text)
			throws IOException {
		GeoCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readGeographicCompat();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Backward Compatible Projected Coordinate Reference System from the
	 * well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Projected Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ProjectedCoordinateReferenceSystem readProjectedCompat(
			String text) throws IOException {
		ProjectedCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readProjectedCompat();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Backward Compatible Projected Geodetic Coordinate Reference System
	 * from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Projected Geodetic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ProjectedCoordinateReferenceSystem readProjectedGeodeticCompat(
			String text) throws IOException {
		ProjectedCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readProjectedGeodeticCompat();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Read a Backward Compatible Projected Geographic Coordinate Reference
	 * System from the well-known text
	 * 
	 * @param text
	 *            well-known text
	 * @return Projected Geographic Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public static ProjectedCoordinateReferenceSystem readProjectedGeographicCompat(
			String text) throws IOException {
		ProjectedCoordinateReferenceSystem crs = null;
		CRSReader reader = new CRSReader(text);
		try {
			crs = reader.readProjectedGeographicCompat();
			reader.readEnd();
		} finally {
			reader.close();
		}
		return crs;
	}

	/**
	 * Text Reader
	 */
	private TextReader reader;

	/**
	 * Strict rule enforcement
	 */
	private boolean strict = false;

	/**
	 * Backward Compatible extras
	 */
	private Map<String, String> extras = new LinkedHashMap<>();

	/**
	 * Constructor
	 * 
	 * @param text
	 *            well-known text
	 */
	public CRSReader(String text) {
		this(new TextReader(text));
	}

	/**
	 * Constructor
	 * 
	 * @param reader
	 *            text reader
	 */
	public CRSReader(TextReader reader) {
		this(reader, false);
	}

	/**
	 * Constructor
	 * 
	 * @param text
	 *            well-known text
	 * @param strict
	 *            strict rule enforcement
	 */
	public CRSReader(String text, boolean strict) {
		this(new TextReader(text), strict);
	}

	/**
	 * Constructor
	 * 
	 * @param reader
	 *            text reader
	 * @param strict
	 *            strict rule enforcement
	 */
	public CRSReader(TextReader reader, boolean strict) {
		this.reader = reader;
		this.strict = strict;
	}

	/**
	 * Get the text reader
	 * 
	 * @return text reader
	 */
	public TextReader getTextReader() {
		return reader;
	}

	/**
	 * Is strict rule enforcement enabled
	 * 
	 * @return true if strict
	 */
	public boolean isStrict() {
		return strict;
	}

	/**
	 * Set the strict rule enforcement setting
	 * 
	 * @param strict
	 *            true for strict enforcement
	 */
	public void setStrict(boolean strict) {
		this.strict = strict;
	}

	/**
	 * Reset the reader
	 * 
	 * @throws IOException
	 *             upon reset error
	 */
	public void reset() throws IOException {
		reader.reset();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		reader.close();
	}

	/**
	 * Read a Coordinate Reference System from the well-known text
	 * 
	 * @return Coordinate Reference System
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystem read() throws IOException {

		CoordinateReferenceSystem crs = null;

		CoordinateReferenceSystemKeyword keyword = peekKeyword();
		switch (keyword) {
		case GEODCRS:
		case GEOGCRS:
			crs = readGeo();
			break;
		case GEOCCS:
		case GEOGCS:
			crs = readGeoCompat();
			break;
		case PROJCRS:
			crs = readProjected();
			break;
		case PROJCS:
			crs = readProjectedCompat();
			break;
		case VERTCRS:
			crs = readVertical();
			break;
		case VERT_CS:
			crs = readVerticalCompat();
			break;
		case ENGCRS:
			crs = readEngineering();
			break;
		case LOCAL_CS:
			crs = readEngineeringCompat();
			break;
		case PARAMETRICCRS:
			crs = readParametric();
			break;
		case TIMECRS:
			crs = readTemporal();
			break;
		case DERIVEDPROJCRS:
			crs = readDerivedProjected();
			break;
		default:
			throw new ProjectionException(
					"Unsupported WKT CRS keyword: " + keyword);
		}

		return crs;
	}

	/**
	 * Read a WKT CRS keyword
	 * 
	 * @return keyword
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword readKeyword() throws IOException {
		return CoordinateReferenceSystemKeyword
				.getRequiredType(reader.readToken());
	}

	/**
	 * Read WKT CRS keywords
	 * 
	 * @return keywords
	 * @throws IOException
	 *             upon failure to read
	 */
	public Set<CoordinateReferenceSystemKeyword> readKeywords()
			throws IOException {
		return CoordinateReferenceSystemKeyword
				.getRequiredTypes(reader.readToken());
	}

	/**
	 * Read a specific WKT CRS keyword, next token when strict, until found when
	 * not
	 * 
	 * @param keywords
	 *            read until one of the keywords
	 * @return keyword read
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword readKeyword(
			CoordinateReferenceSystemKeyword... keywords) throws IOException {
		return readKeyword(true, keywords);
	}

	/**
	 * Read skipping tokens up until before an optional WKT CRS keyword
	 * 
	 * @param keywords
	 *            read until one of the keywords
	 * @return next keyword or null
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword readToKeyword(
			CoordinateReferenceSystemKeyword... keywords) throws IOException {
		CoordinateReferenceSystemKeyword keyword = readKeyword(false, keywords);
		if (keyword != null) {
			reader.pushToken(keyword.name());
		}
		return keyword;
	}

	/**
	 * Read looking for a specific WKT CRS keyword, skipping others if not
	 * strict mode
	 * 
	 * @param required
	 *            true if keyword is required, read only until an external right
	 *            delimiter
	 * @param keywords
	 *            read until one of the keywords
	 * @return keyword read
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword readKeyword(boolean required,
			CoordinateReferenceSystemKeyword... keywords) throws IOException {

		CoordinateReferenceSystemKeyword keyword = null;
		Set<CoordinateReferenceSystemKeyword> keywordSet = new HashSet<>(
				Arrays.asList(keywords));

		int delimiterCount = 0;

		String previousToken = null;
		String token = reader.readToken();

		StringBuilder ignored = null;
		while (token != null) {

			if (!required) {
				if (WKTUtils.isLeftDelimiter(token)) {
					delimiterCount++;
				} else if (WKTUtils.isRightDelimiter(token)) {
					delimiterCount--;
					if (delimiterCount < 0) {
						reader.pushToken(token);
						break;
					}
				}
			}

			Set<CoordinateReferenceSystemKeyword> tokenKeywords = CoordinateReferenceSystemKeyword
					.getTypes(token);
			if (tokenKeywords != null) {
				for (CoordinateReferenceSystemKeyword kw : tokenKeywords) {
					if (keywordSet.contains(kw)) {
						keyword = kw;
						break;
					}
				}
				if (keyword != null) {
					break;
				}
			}

			if (previousToken != null) {
				if (ignored == null) {
					ignored = new StringBuilder();
				}
				ignored.append(previousToken);
			}

			previousToken = token;
			token = reader.readToken();
		}

		if (required && keyword == null) {
			throw new ProjectionException(
					"Expected keyword not found: " + keywordNames(keywordSet));
		}

		if (previousToken != null && (keyword == null
				|| !previousToken.equals(WKTConstants.SEPARATOR))) {
			if (ignored == null) {
				ignored = new StringBuilder();
			}
			ignored.append(previousToken);
		}

		if (ignored != null) {
			StringBuilder log = new StringBuilder();
			if (strict) {
				log.append("Unexpected");
			} else {
				log.append("Ignored");
			}
			if (keyword != null) {
				log.append(" before ");
				log.append(keyword.getKeywords());
			}
			log.append(": \"");
			log.append(ignored);
			log.append("\"");
			if (strict) {
				throw new ProjectionException(log.toString());
			} else {
				logger.log(Level.WARNING, log.toString());
			}
		}

		return keyword;
	}

	/**
	 * Peek a WKT CRS keyword
	 * 
	 * @return keyword
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword peekKeyword() throws IOException {
		return CoordinateReferenceSystemKeyword
				.getRequiredType(reader.peekToken());
	}

	/**
	 * Peek WKT CRS keywords
	 * 
	 * @return keywords
	 * @throws IOException
	 *             upon failure to read
	 */
	public Set<CoordinateReferenceSystemKeyword> peekKeywords()
			throws IOException {
		return CoordinateReferenceSystemKeyword
				.getRequiredTypes(reader.peekToken());
	}

	/**
	 * Peek an optional WKT CRS keyword
	 * 
	 * @return keyword
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword peekOptionalKeyword()
			throws IOException {
		return CoordinateReferenceSystemKeyword.getType(reader.peekToken());
	}

	/**
	 * Peek an optional WKT CRS keywords
	 * 
	 * @return keywords
	 * @throws IOException
	 *             upon failure to read
	 */
	public Set<CoordinateReferenceSystemKeyword> peekOptionalKeywords()
			throws IOException {
		return CoordinateReferenceSystemKeyword.getTypes(reader.peekToken());
	}

	/**
	 * Peek an optional WKT CRS keyword
	 * 
	 * @param num
	 *            number of tokens out to peek at
	 * @return keyword
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystemKeyword peekOptionalKeyword(int num)
			throws IOException {
		return CoordinateReferenceSystemKeyword.getType(reader.peekToken(num));
	}

	/**
	 * Peek an optional WKT CRS keywords
	 * 
	 * @param num
	 *            number of tokens out to peek at
	 * @return keywords
	 * @throws IOException
	 *             upon failure to read
	 */
	public Set<CoordinateReferenceSystemKeyword> peekOptionalKeywords(int num)
			throws IOException {
		return CoordinateReferenceSystemKeyword.getTypes(reader.peekToken(num));
	}

	/**
	 * Read a left delimiter
	 * 
	 * @throws IOException
	 *             upon failure to read
	 */
	public void readLeftDelimiter() throws IOException {
		String token = reader.readExpectedToken();
		if (!WKTUtils.isLeftDelimiter(token)) {
			throw new ProjectionException(
					"Invalid left delimiter token, expected '[' or '('. found: '"
							+ token + "'");
		}
	}

	/**
	 * Peek if the next token is a left delimiter
	 * 
	 * @return true if next token is a left delimiter
	 * @throws IOException
	 *             upon failure to read
	 */
	public boolean peekLeftDelimiter() throws IOException {
		boolean leftDelimiter = false;
		String token = reader.peekToken();
		if (token != null) {
			leftDelimiter = WKTUtils.isLeftDelimiter(token);
		}
		return leftDelimiter;
	}

	/**
	 * Read skipping tokens until an external right delimiter (first right
	 * delimiter without a preceding left)
	 * 
	 * @throws IOException
	 *             upon failure to read
	 */
	public void readRightDelimiter() throws IOException {
		readKeyword(false, new CoordinateReferenceSystemKeyword[] {});
		String token = reader.readExpectedToken();
		if (!WKTUtils.isRightDelimiter(token)) {
			throw new ProjectionException(
					"Invalid right delimiter token, expected ']' or ')'. found: '"
							+ token + "'");
		}
	}

	/**
	 * Peek if the next token is a right delimiter
	 * 
	 * @return true if next token is a right delimiter
	 * @throws IOException
	 *             upon failure to read
	 */
	public boolean peekRightDelimiter() throws IOException {
		boolean rightDelimiter = false;
		String token = reader.peekToken();
		if (token != null) {
			rightDelimiter = WKTUtils.isRightDelimiter(token);
		}
		return rightDelimiter;
	}

	/**
	 * Read a WKT Separator (comma)
	 * 
	 * @throws IOException
	 *             upon failure to read
	 */
	public void readSeparator() throws IOException {
		String token = reader.peekToken();
		if (token.equals(WKTConstants.SEPARATOR)) {
			reader.readExpectedToken();
		} else if (strict) {
			throw new ProjectionException(
					"Invalid separator token, expected ','. found: '" + token
							+ "'");
		} else {
			logger.log(Level.WARNING,
					"Missing expected separator before token: '" + token + "'");
		}
	}

	/**
	 * Peek if the next token is a WKT Separator (comma)
	 * 
	 * @return true if next token is a separator
	 * @throws IOException
	 *             upon failure to read
	 */
	public boolean peekSeparator() throws IOException {
		boolean separator = false;
		String token = reader.peekToken();
		if (token != null) {
			separator = token.equals(WKTConstants.SEPARATOR);
		}
		return separator;
	}

	/**
	 * "Read" an expected end, checking for unexpected trailing tokens
	 * 
	 * @throws IOException
	 *             upon failure to read
	 */
	public void readEnd() throws IOException {

		String token = reader.readToken();
		if (token != null) {
			StringBuilder ignored = new StringBuilder();
			do {
				ignored.append(token);
				token = reader.readToken();
			} while (token != null);

			StringBuilder log = new StringBuilder();
			if (strict) {
				log.append("Unexpected");
			} else {
				log.append("Ignored");
			}
			log.append(" end: \"");
			log.append(ignored);
			log.append("\"");
			if (strict) {
				throw new ProjectionException(log.toString());
			} else {
				logger.log(Level.WARNING, log.toString());
			}
		}
	}

	/**
	 * Read a keyword delimited token
	 * 
	 * @param keyword
	 *            expected keyword
	 * @return token
	 * @throws IOException
	 *             upon failure to read
	 */
	public String readKeywordDelimitedToken(
			CoordinateReferenceSystemKeyword keyword) throws IOException {

		readKeyword(keyword);

		readLeftDelimiter();

		String token = reader.readExpectedToken();

		readRightDelimiter();

		return token;
	}

	/**
	 * Validate the keyword against the expected keywords
	 * 
	 * @param keywords
	 *            keywords
	 * @param expected
	 *            expected keywords
	 * @return matching keyword
	 */
	private CoordinateReferenceSystemKeyword validateKeyword(
			Set<CoordinateReferenceSystemKeyword> keywords,
			CoordinateReferenceSystemKeyword... expected) {
		CoordinateReferenceSystemKeyword keyword = null;
		Set<CoordinateReferenceSystemKeyword> expectedSet = new HashSet<>(
				Arrays.asList(expected));
		for (CoordinateReferenceSystemKeyword kw : keywords) {
			if (expectedSet.contains(kw)) {
				keyword = kw;
				break;
			}
		}
		if (keyword == null) {
			throw new ProjectionException(
					"Unexpected keyword. found: " + keywordNames(keywords)
							+ ", expected: " + keywordNames(expectedSet));
		}
		return keyword;
	}

	/**
	 * Set of all keyword names from the set of keywords
	 * 
	 * @param keywords
	 *            keywords
	 * @return set of names
	 */
	private Set<String> keywordNames(
			Set<CoordinateReferenceSystemKeyword> keywords) {
		Set<String> names = new LinkedHashSet<>();
		for (CoordinateReferenceSystemKeyword keyword : keywords) {
			names.addAll(keyword.getKeywords());
		}
		return names;
	}

	/**
	 * Check if the keyword is next following an immediate next separator
	 * 
	 * @param keywords
	 *            keywords
	 * @return true if next
	 * @throws IOException
	 *             upon failure to read
	 */
	private boolean isKeywordNext(CoordinateReferenceSystemKeyword... keywords)
			throws IOException {
		boolean next = false;
		boolean separator = peekSeparator();
		if (separator || !strict) {
			int num = separator ? 2 : 1;
			Set<CoordinateReferenceSystemKeyword> nextKeywords = peekOptionalKeywords(
					num);
			if (nextKeywords != null && !nextKeywords.isEmpty()) {
				for (CoordinateReferenceSystemKeyword keyword : keywords) {
					next = nextKeywords.contains(keyword);
					if (next) {
						break;
					}
				}
			}
		}
		return next;
	}

	/**
	 * Check if the keyword is next following an immediate next separator
	 * 
	 * @param keyword
	 *            keyword
	 * @return true if next
	 * @throws IOException
	 *             upon failure to read
	 */
	private boolean isNonKeywordNext() throws IOException {
		boolean next = false;
		if (peekSeparator()) {
			Set<CoordinateReferenceSystemKeyword> nextKeywords = peekOptionalKeywords(
					2);
			next = nextKeywords == null || nextKeywords.isEmpty();
		}
		return next;
	}

	/**
	 * Is a unit next following an immediate next separator
	 * 
	 * @return true if next
	 * @throws IOException
	 *             upon failure to read
	 */
	private boolean isUnitNext() throws IOException {
		return isKeywordNext(CoordinateReferenceSystemKeyword.ANGLEUNIT,
				CoordinateReferenceSystemKeyword.LENGTHUNIT,
				CoordinateReferenceSystemKeyword.PARAMETRICUNIT,
				CoordinateReferenceSystemKeyword.SCALEUNIT,
				CoordinateReferenceSystemKeyword.TIMEUNIT);
	}

	/**
	 * Is a spatial unit next following an immediate next separator
	 * 
	 * @return true if next
	 * @throws IOException
	 *             upon failure to read
	 */
	private boolean isSpatialUnitNext() throws IOException {
		return isKeywordNext(CoordinateReferenceSystemKeyword.ANGLEUNIT,
				CoordinateReferenceSystemKeyword.LENGTHUNIT,
				CoordinateReferenceSystemKeyword.PARAMETRICUNIT,
				CoordinateReferenceSystemKeyword.SCALEUNIT);
	}

	/**
	 * Is a time unit next following an immediate next separator
	 * 
	 * @return true if next
	 * @throws IOException
	 *             upon failure to read
	 */
	private boolean isTimeUnitNext() throws IOException {
		return isKeywordNext(CoordinateReferenceSystemKeyword.TIMEUNIT);
	}

	/**
	 * Read a Geodetic or Geographic CRS
	 * 
	 * @return geodetic, geographic, or derived coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystem readGeo() throws IOException {

		GeoCoordinateReferenceSystem baseCrs = new GeoCoordinateReferenceSystem();
		CoordinateReferenceSystem crs = baseCrs;
		DerivedCoordinateReferenceSystem derivedCrs = null;

		CoordinateReferenceSystemKeyword keyword = readKeyword(
				CoordinateReferenceSystemKeyword.GEODCRS,
				CoordinateReferenceSystemKeyword.GEOGCRS);
		crs.setType(WKTUtils.getCoordinateReferenceSystemType(keyword));

		readLeftDelimiter();

		String name = reader.readExpectedToken();

		if (isKeywordNext(CoordinateReferenceSystemKeyword.BASEGEODCRS,
				CoordinateReferenceSystemKeyword.BASEGEOGCRS)) {

			switch (keyword) {
			case GEODCRS:
				readKeyword(CoordinateReferenceSystemKeyword.BASEGEODCRS);
				break;
			case GEOGCRS:
				readKeyword(CoordinateReferenceSystemKeyword.BASEGEOGCRS);
				break;
			default:
				throw new ProjectionException(
						"Unsupported Coordinate Reference System Type: "
								+ keyword);
			}

			derivedCrs = new DerivedCoordinateReferenceSystem();
			derivedCrs.setBase(baseCrs);
			crs = derivedCrs;

			readLeftDelimiter();
			baseCrs.setName(reader.readExpectedToken());
		}

		crs.setName(name);

		boolean isDynamic = isKeywordNext(
				CoordinateReferenceSystemKeyword.DYNAMIC);
		if (isDynamic) {
			readSeparator();
			baseCrs.setDynamic(readDynamic());
		}

		if (isDynamic
				|| isKeywordNext(CoordinateReferenceSystemKeyword.DATUM)) {
			readSeparator();
			GeoReferenceFrame referenceFrame = readGeoReferenceFrame();
			referenceFrame.setType(baseCrs.getType());
			baseCrs.setReferenceFrame(referenceFrame);
		} else if (isKeywordNext(CoordinateReferenceSystemKeyword.ENSEMBLE)) {
			readSeparator();
			baseCrs.setDatumEnsemble(readGeoDatumEnsemble());
		} else {
			// Validation error
			readSeparator();
			readKeyword(CoordinateReferenceSystemKeyword.DATUM,
					CoordinateReferenceSystemKeyword.ENSEMBLE);
		}

		if (derivedCrs != null) {

			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
			if (keyword == CoordinateReferenceSystemKeyword.ID) {
				baseCrs.setIdentifiers(readIdentifiers());
			}

			readRightDelimiter();

			readSeparator();
			derivedCrs.setConversion(readDerivingConversion());

		}

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read a Projected CRS
	 * 
	 * @return projected coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjected()
			throws IOException {
		CoordinateReferenceSystemType expectedType = null;
		return readProjected(expectedType);
	}

	/**
	 * Read a Projected Geodetic CRS
	 * 
	 * @return projected geodetic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjectedGeodetic()
			throws IOException {
		return readProjected(CoordinateReferenceSystemType.GEODETIC);
	}

	/**
	 * Read a Projected Geographic CRS
	 * 
	 * @return projected geographic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjectedGeographic()
			throws IOException {
		return readProjected(CoordinateReferenceSystemType.GEOGRAPHIC);
	}

	/**
	 * Read a Projected CRS
	 * 
	 * @param expectedBaseType
	 *            expected base coordinate reference system type
	 * 
	 * @return projected coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjected(
			CoordinateReferenceSystemType expectedBaseType) throws IOException {

		ProjectedCoordinateReferenceSystem crs = new ProjectedCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.PROJCRS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();

		CoordinateReferenceSystemKeyword type = readKeyword(
				CoordinateReferenceSystemKeyword.BASEGEODCRS,
				CoordinateReferenceSystemKeyword.BASEGEOGCRS);
		CoordinateReferenceSystemType crsType = WKTUtils
				.getCoordinateReferenceSystemType(type);
		if (expectedBaseType != null && crsType != expectedBaseType) {
			throw new ProjectionException(
					"Unexpected Base Coordinate Reference System Type. expected: "
							+ expectedBaseType + ", found: " + crsType);
		}
		crs.setBaseType(crsType);

		readLeftDelimiter();

		crs.setBaseName(reader.readExpectedToken());

		boolean isDynamic = isKeywordNext(
				CoordinateReferenceSystemKeyword.DYNAMIC);
		if (isDynamic) {
			readSeparator();
			crs.setDynamic(readDynamic());
		}

		if (isDynamic
				|| isKeywordNext(CoordinateReferenceSystemKeyword.DATUM)) {
			readSeparator();
			GeoReferenceFrame referenceFrame = readGeoReferenceFrame();
			referenceFrame.setType(crsType);
			crs.setReferenceFrame(referenceFrame);
		} else if (isKeywordNext(CoordinateReferenceSystemKeyword.ENSEMBLE)) {
			readSeparator();
			crs.setDatumEnsemble(readGeoDatumEnsemble());
		} else {
			// Validation error
			readSeparator();
			readKeyword(CoordinateReferenceSystemKeyword.DATUM,
					CoordinateReferenceSystemKeyword.ENSEMBLE);
		}

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ANGLEUNIT,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.ANGLEUNIT) {
			crs.setUnit(readAngleUnit());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			crs.setBaseIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		readSeparator();

		crs.setMapProjection(readMapProjection());

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read a Vertical CRS
	 * 
	 * @return vertical coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateReferenceSystem readVertical() throws IOException {

		VerticalCoordinateReferenceSystem baseCrs = new VerticalCoordinateReferenceSystem();
		CoordinateReferenceSystem crs = baseCrs;
		DerivedCoordinateReferenceSystem derivedCrs = null;

		readKeyword(CoordinateReferenceSystemKeyword.VERTCRS);

		readLeftDelimiter();

		String name = reader.readExpectedToken();

		if (isKeywordNext(CoordinateReferenceSystemKeyword.BASEVERTCRS)) {
			readKeyword(CoordinateReferenceSystemKeyword.BASEVERTCRS);

			derivedCrs = new DerivedCoordinateReferenceSystem();
			derivedCrs.setBase(baseCrs);
			crs = derivedCrs;

			readLeftDelimiter();
			baseCrs.setName(reader.readExpectedToken());
		}

		crs.setName(name);

		boolean isDynamic = isKeywordNext(
				CoordinateReferenceSystemKeyword.DYNAMIC);
		if (isDynamic) {
			readSeparator();
			baseCrs.setDynamic(readDynamic());
		}

		if (isDynamic
				|| isKeywordNext(CoordinateReferenceSystemKeyword.VDATUM)) {
			readSeparator();
			baseCrs.setReferenceFrame(readVerticalReferenceFrame());
		} else if (isKeywordNext(CoordinateReferenceSystemKeyword.ENSEMBLE)) {
			readSeparator();
			baseCrs.setDatumEnsemble(readVerticalDatumEnsemble());
		} else {
			// Validation error
			readSeparator();
			readKeyword(CoordinateReferenceSystemKeyword.VDATUM,
					CoordinateReferenceSystemKeyword.ENSEMBLE);
		}

		if (derivedCrs != null) {

			CoordinateReferenceSystemKeyword keyword = readToKeyword(
					CoordinateReferenceSystemKeyword.ID);
			if (keyword == CoordinateReferenceSystemKeyword.ID) {
				baseCrs.setIdentifiers(readIdentifiers());
			}

			readRightDelimiter();

			readSeparator();
			derivedCrs.setConversion(readDerivingConversion());

		}

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		if (derivedCrs == null
				&& isKeywordNext(CoordinateReferenceSystemKeyword.GEOIDMODEL)) {
			readSeparator();
			readKeyword(CoordinateReferenceSystemKeyword.GEOIDMODEL);
			readLeftDelimiter();
			baseCrs.setGeoidModelName(reader.readExpectedToken());
			CoordinateReferenceSystemKeyword keyword = readToKeyword(
					CoordinateReferenceSystemKeyword.ID);
			if (keyword == CoordinateReferenceSystemKeyword.ID) {
				baseCrs.setGeoidModelIdentifier(readIdentifier());
			}
			readRightDelimiter();
		}

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read an Engineering CRS
	 * 
	 * @return engineering coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public EngineeringCoordinateReferenceSystem readEngineering()
			throws IOException {

		EngineeringCoordinateReferenceSystem crs = new EngineeringCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.ENGCRS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();

		crs.setEngineeringDatum(readEngineeringDatum());

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read a Parametric CRS
	 * 
	 * @return parametric coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ParametricCoordinateReferenceSystem readParametric()
			throws IOException {

		ParametricCoordinateReferenceSystem crs = new ParametricCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.PARAMETRICCRS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();

		crs.setDatum(readParametricDatum());

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read a Temporal CRS
	 * 
	 * @return temporal coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public TemporalCoordinateReferenceSystem readTemporal() throws IOException {

		TemporalCoordinateReferenceSystem crs = new TemporalCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.TIMECRS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();

		crs.setTemporalDatum(readTemporalDatum());

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read a Derived Projected CRS
	 * 
	 * @return derived coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public DerivedCoordinateReferenceSystem readDerivedProjected()
			throws IOException {

		DerivedCoordinateReferenceSystem crs = new DerivedCoordinateReferenceSystem();
		ProjectedCoordinateReferenceSystem projectedCrs = new ProjectedCoordinateReferenceSystem();
		crs.setBase(projectedCrs);

		readKeyword(CoordinateReferenceSystemKeyword.DERIVEDPROJCRS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();

		readKeyword(CoordinateReferenceSystemKeyword.BASEPROJCRS);

		readLeftDelimiter();

		projectedCrs.setName(reader.readExpectedToken());

		readSeparator();

		CoordinateReferenceSystemKeyword keyword = readKeyword(
				CoordinateReferenceSystemKeyword.BASEGEODCRS,
				CoordinateReferenceSystemKeyword.BASEGEOGCRS);
		projectedCrs.setBaseType(
				WKTUtils.getCoordinateReferenceSystemType(keyword));

		readLeftDelimiter();
		projectedCrs.setBaseName(reader.readExpectedToken());

		boolean isDynamic = isKeywordNext(
				CoordinateReferenceSystemKeyword.DYNAMIC);
		if (isDynamic) {
			readSeparator();
			projectedCrs.setDynamic(readDynamic());
		}

		if (isDynamic
				|| isKeywordNext(CoordinateReferenceSystemKeyword.DATUM)) {
			readSeparator();
			GeoReferenceFrame referenceFrame = readGeoReferenceFrame();
			referenceFrame.setType(projectedCrs.getBaseType());
			projectedCrs.setReferenceFrame(referenceFrame);
		} else if (isKeywordNext(CoordinateReferenceSystemKeyword.ENSEMBLE)) {
			readSeparator();
			projectedCrs.setDatumEnsemble(readGeoDatumEnsemble());
		} else {
			// Validation error
			readSeparator();
			readKeyword(CoordinateReferenceSystemKeyword.DATUM,
					CoordinateReferenceSystemKeyword.ENSEMBLE);
		}

		keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			projectedCrs.setBaseIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		readSeparator();

		projectedCrs.setMapProjection(readMapProjection());

		keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			projectedCrs.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		readSeparator();

		crs.setConversion(readDerivingConversion());

		readSeparator();

		crs.setCoordinateSystem(readCoordinateSystem());

		readScopeExtentIdentifierRemark(crs);

		readRightDelimiter();

		return crs;
	}

	/**
	 * Read the usages (scope and extent), identifiers, and remark into the CRS
	 * 
	 * @param crs
	 *            coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public void readScopeExtentIdentifierRemark(CoordinateReferenceSystem crs)
			throws IOException {

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.USAGE,
				CoordinateReferenceSystemKeyword.ID,
				CoordinateReferenceSystemKeyword.REMARK);

		if (keyword == CoordinateReferenceSystemKeyword.USAGE) {
			crs.setUsages(readUsages());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID,
					CoordinateReferenceSystemKeyword.REMARK);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			crs.setIdentifiers(readIdentifiers());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.REMARK);
		}

		if (keyword == CoordinateReferenceSystemKeyword.REMARK) {
			crs.setRemark(readRemark());
		}

	}

	/**
	 * Read a Geo reference frame
	 * 
	 * @return geo reference frame
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeoReferenceFrame readGeoReferenceFrame() throws IOException {
		ReferenceFrame referenceFrame = readReferenceFrame();
		if (!(referenceFrame instanceof GeoReferenceFrame)) {
			throw new ProjectionException(
					"Reference frame was not an expected Geo Reference Frame");
		}
		return (GeoReferenceFrame) referenceFrame;
	}

	/**
	 * Read a Vertical reference frame
	 * 
	 * @return vertical reference frame
	 * @throws IOException
	 *             upon failure to read
	 */
	public VerticalReferenceFrame readVerticalReferenceFrame()
			throws IOException {
		ReferenceFrame referenceFrame = readReferenceFrame();
		if (!(referenceFrame instanceof VerticalReferenceFrame)) {
			throw new ProjectionException(
					"Reference frame was not an expected Vertical Reference Frame");
		}
		return (VerticalReferenceFrame) referenceFrame;
	}

	/**
	 * Read an Engineering datum
	 * 
	 * @return engineering datum
	 * @throws IOException
	 *             upon failure to read
	 */
	public EngineeringDatum readEngineeringDatum() throws IOException {
		ReferenceFrame referenceFrame = readReferenceFrame();
		if (!(referenceFrame instanceof EngineeringDatum)) {
			throw new ProjectionException(
					"Reference frame was not an expected Engineering Datum");
		}
		return (EngineeringDatum) referenceFrame;
	}

	/**
	 * Read a Parametric datum
	 * 
	 * @return parametric datum
	 * @throws IOException
	 *             upon failure to read
	 */
	public ParametricDatum readParametricDatum() throws IOException {
		ReferenceFrame referenceFrame = readReferenceFrame();
		if (!(referenceFrame instanceof ParametricDatum)) {
			throw new ProjectionException(
					"Reference frame was not an expected Parametric Datum");
		}
		return (ParametricDatum) referenceFrame;
	}

	/**
	 * Read a Reference frame (datum)
	 * 
	 * @return reference frame
	 * @throws IOException
	 *             upon failure to read
	 */
	public ReferenceFrame readReferenceFrame() throws IOException {

		ReferenceFrame referenceFrame = null;
		GeoReferenceFrame geoReferenceFrame = null;

		CoordinateReferenceSystemKeyword type = readKeyword(
				CoordinateReferenceSystemKeyword.DATUM,
				CoordinateReferenceSystemKeyword.VDATUM,
				CoordinateReferenceSystemKeyword.EDATUM,
				CoordinateReferenceSystemKeyword.PDATUM);
		switch (type) {
		case DATUM:
			geoReferenceFrame = new GeoReferenceFrame();
			referenceFrame = geoReferenceFrame;
			break;
		case VDATUM:
			referenceFrame = new VerticalReferenceFrame();
			break;
		case EDATUM:
			referenceFrame = new EngineeringDatum();
			break;
		case PDATUM:
			referenceFrame = new ParametricDatum();
			break;
		default:
			throw new ProjectionException(
					"Unexpected Reference Frame type: " + type);
		}

		readLeftDelimiter();

		referenceFrame.setName(reader.readExpectedToken());

		if (geoReferenceFrame != null) {
			readSeparator();
			geoReferenceFrame.setEllipsoid(readEllipsoid());
		}

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ANCHOR,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.ANCHOR) {
			referenceFrame.setAnchor(readKeywordDelimitedToken(
					CoordinateReferenceSystemKeyword.ANCHOR));
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			referenceFrame.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		if (geoReferenceFrame != null
				&& isKeywordNext(CoordinateReferenceSystemKeyword.PRIMEM)) {
			readSeparator();
			geoReferenceFrame.setPrimeMeridian(readPrimeMeridian());
		}

		return referenceFrame;
	}

	/**
	 * Read a Geo datum ensemble
	 * 
	 * @return geo datum ensemble
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeoDatumEnsemble readGeoDatumEnsemble() throws IOException {
		DatumEnsemble datumEnsemble = readDatumEnsemble();
		if (!(datumEnsemble instanceof GeoDatumEnsemble)) {
			throw new ProjectionException(
					"Datum ensemble was not an expected Geo Datum Ensemble");
		}
		return (GeoDatumEnsemble) datumEnsemble;
	}

	/**
	 * Read a Vertical datum ensemble
	 * 
	 * @return vertical datum ensemble
	 * @throws IOException
	 *             upon failure to read
	 */
	public VerticalDatumEnsemble readVerticalDatumEnsemble()
			throws IOException {
		DatumEnsemble datumEnsemble = readDatumEnsemble();
		if (!(datumEnsemble instanceof VerticalDatumEnsemble)) {
			throw new ProjectionException(
					"Datum ensemble was not an expected Vertical Datum Ensemble");
		}
		return (VerticalDatumEnsemble) datumEnsemble;
	}

	/**
	 * Read a Datum ensemble
	 * 
	 * @return datum ensemble
	 * @throws IOException
	 *             upon failure to read
	 */
	public DatumEnsemble readDatumEnsemble() throws IOException {

		readKeyword(CoordinateReferenceSystemKeyword.ENSEMBLE);

		readLeftDelimiter();

		String name = reader.readExpectedToken();

		List<DatumEnsembleMember> members = new ArrayList<>();
		do {

			readSeparator();

			members.add(readDatumEnsembleMember());

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.MEMBER));

		DatumEnsemble datumEnsemble = null;
		GeoDatumEnsemble geoDatumEnsemble = null;

		if (isKeywordNext(CoordinateReferenceSystemKeyword.ELLIPSOID)) {
			geoDatumEnsemble = new GeoDatumEnsemble();
			datumEnsemble = geoDatumEnsemble;
		} else {
			datumEnsemble = new VerticalDatumEnsemble();
		}

		datumEnsemble.setName(name);
		datumEnsemble.setMembers(members);

		if (geoDatumEnsemble != null) {
			readSeparator();
			geoDatumEnsemble.setEllipsoid(readEllipsoid());
		}

		readSeparator();
		readKeyword(CoordinateReferenceSystemKeyword.ENSEMBLEACCURACY);

		readLeftDelimiter();

		datumEnsemble.setAccuracy(reader.readNumber());

		readRightDelimiter();

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			datumEnsemble.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		if (geoDatumEnsemble != null
				&& isKeywordNext(CoordinateReferenceSystemKeyword.PRIMEM)) {
			// TODO http://ogc.standardstracker.org/show_request.cgi?id=672
			readSeparator();
			geoDatumEnsemble.setPrimeMeridian(readPrimeMeridian());
		}

		return datumEnsemble;
	}

	/**
	 * Read a Datum ensemble member
	 * 
	 * @return datum ensemble member
	 * @throws IOException
	 *             upon failure to read
	 */
	public DatumEnsembleMember readDatumEnsembleMember() throws IOException {

		DatumEnsembleMember member = new DatumEnsembleMember();

		readKeyword(CoordinateReferenceSystemKeyword.MEMBER);

		readLeftDelimiter();

		member.setName(reader.readExpectedToken());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			member.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return member;
	}

	/**
	 * Read a Dynamic coordinate reference system
	 * 
	 * @return dynamic
	 * @throws IOException
	 *             upon failure to read
	 */
	public Dynamic readDynamic() throws IOException {

		Dynamic dynamic = new Dynamic();

		readKeyword(CoordinateReferenceSystemKeyword.DYNAMIC);

		readLeftDelimiter();

		readKeyword(CoordinateReferenceSystemKeyword.FRAMEEPOCH);

		readLeftDelimiter();

		dynamic.setReferenceEpoch(reader.readUnsignedNumber());

		readRightDelimiter();

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.MODEL);
		if (keyword == CoordinateReferenceSystemKeyword.MODEL) {

			readKeyword(CoordinateReferenceSystemKeyword.MODEL);

			readLeftDelimiter();

			dynamic.setDeformationModelName(reader.readExpectedToken());

			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
			if (keyword == CoordinateReferenceSystemKeyword.ID) {
				dynamic.setDeformationModelIdentifiers(readIdentifiers());
			}

			readRightDelimiter();
		}

		readRightDelimiter();

		return dynamic;
	}

	/**
	 * Read a Prime meridian
	 * 
	 * @return prime meridian
	 * @throws IOException
	 *             upon failure to read
	 */
	public PrimeMeridian readPrimeMeridian() throws IOException {

		PrimeMeridian primeMeridian = new PrimeMeridian();

		readKeyword(CoordinateReferenceSystemKeyword.PRIMEM);

		readLeftDelimiter();

		primeMeridian.setName(reader.readExpectedToken());

		readSeparator();

		primeMeridian.setIrmLongitude(reader.readNumber());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ANGLEUNIT,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.ANGLEUNIT) {
			primeMeridian.setIrmLongitudeUnit(readAngleUnit());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			primeMeridian.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return primeMeridian;
	}

	/**
	 * Read an Ellipsoid
	 * 
	 * @return ellipsoid
	 * @throws IOException
	 *             upon failure to read
	 */
	public Ellipsoid readEllipsoid() throws IOException {

		Ellipsoid ellipsoid = new Ellipsoid();

		readKeyword(CoordinateReferenceSystemKeyword.ELLIPSOID);

		readLeftDelimiter();

		ellipsoid.setName(reader.readExpectedToken());

		readSeparator();

		ellipsoid.setSemiMajorAxis(reader.readUnsignedNumber());

		readSeparator();

		ellipsoid.setInverseFlattening(reader.readUnsignedNumber());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.LENGTHUNIT,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.LENGTHUNIT) {
			ellipsoid.setUnit(readLengthUnit());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			ellipsoid.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return ellipsoid;
	}

	/**
	 * Read a Unit
	 * 
	 * @return unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readUnit() throws IOException {
		return readUnit(UnitType.UNIT);
	}

	/**
	 * Read an Angle Unit
	 * 
	 * @return angle unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readAngleUnit() throws IOException {
		return readUnit(UnitType.ANGLEUNIT);
	}

	/**
	 * Read a Length Unit
	 * 
	 * @return length unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readLengthUnit() throws IOException {
		return readUnit(UnitType.LENGTHUNIT);
	}

	/**
	 * Read a Parametric Unit
	 * 
	 * @return parametric unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readParametricUnit() throws IOException {
		return readUnit(UnitType.PARAMETRICUNIT);
	}

	/**
	 * Read a Scale Unit
	 * 
	 * @return scale unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readScaleUnit() throws IOException {
		return readUnit(UnitType.SCALEUNIT);
	}

	/**
	 * Read a Time Unit
	 * 
	 * @return time unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readTimeUnit() throws IOException {
		return readUnit(UnitType.TIMEUNIT);
	}

	/**
	 * Read a Unit
	 * 
	 * @param type
	 *            expected unit type
	 * @return unit
	 * @throws IOException
	 *             upon failure to read
	 */
	public Unit readUnit(UnitType type) throws IOException {

		Unit unit = new Unit();

		Set<CoordinateReferenceSystemKeyword> keywords = readKeywords();
		if (type != UnitType.UNIT) {
			CoordinateReferenceSystemKeyword crsType = CoordinateReferenceSystemKeyword
					.getType(type.name());
			validateKeyword(keywords, crsType);
		} else if (keywords.size() == 1) {
			type = WKTUtils.getUnitType(keywords.iterator().next());
		} else if (keywords.isEmpty()) {
			throw new ProjectionException("Unexpected unit keyword. found: "
					+ keywordNames(keywords));
		}
		unit.setType(type);

		readLeftDelimiter();

		unit.setName(reader.readExpectedToken());

		if (type != UnitType.TIMEUNIT || isNonKeywordNext()) {
			readSeparator();
			unit.setConversionFactor(reader.readUnsignedNumber());
		}

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			unit.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return unit;
	}

	/**
	 * Read Identifiers
	 * 
	 * @return identifiers
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<Identifier> readIdentifiers() throws IOException {

		List<Identifier> identifiers = new ArrayList<>();

		do {

			if (!identifiers.isEmpty()) {
				readSeparator();
			}

			identifiers.add(readIdentifier());

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.ID));

		return identifiers;
	}

	/**
	 * Read an Identifier
	 * 
	 * @return identifier
	 * @throws IOException
	 *             upon failure to read
	 */
	public Identifier readIdentifier() throws IOException {

		Identifier identifier = new Identifier();

		readKeyword(CoordinateReferenceSystemKeyword.ID);

		readLeftDelimiter();

		identifier.setName(reader.readExpectedToken());

		readSeparator();

		identifier.setUniqueIdentifier(reader.readExpectedToken());

		if (isNonKeywordNext()) {
			readSeparator();
			identifier.setVersion(reader.readExpectedToken());
		}

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.CITATION,
				CoordinateReferenceSystemKeyword.URI);

		if (keyword == CoordinateReferenceSystemKeyword.CITATION) {
			identifier.setCitation(readKeywordDelimitedToken(
					CoordinateReferenceSystemKeyword.CITATION));
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.URI);
		}

		if (keyword == CoordinateReferenceSystemKeyword.URI) {
			identifier.setUri(readKeywordDelimitedToken(
					CoordinateReferenceSystemKeyword.URI));
		}

		readRightDelimiter();

		return identifier;
	}

	/**
	 * Read a Coordinate system
	 * 
	 * @return coordinate system
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateSystem readCoordinateSystem() throws IOException {

		CoordinateSystem coordinateSystem = new CoordinateSystem();

		readKeyword(CoordinateReferenceSystemKeyword.CS);

		readLeftDelimiter();

		String csTypeName = reader.readToken();
		CoordinateSystemType csType = CoordinateSystemType.getType(csTypeName);
		if (csType == null) {
			throw new ProjectionException(
					"Unexpected coordinate system type. found: " + csTypeName);
		}
		coordinateSystem.setType(csType);

		readSeparator();

		coordinateSystem.setDimension(reader.readUnsignedInteger());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			coordinateSystem.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		readSeparator();
		coordinateSystem.setAxes(readAxes(csType));

		if (WKTUtils.isSpatial(csType)) {

			if (isUnitNext()) {

				readSeparator();

				coordinateSystem.setUnit(readUnit());

			}

		}

		return coordinateSystem;
	}

	/**
	 * Read Axes
	 * 
	 * @return axes
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<Axis> readAxes() throws IOException {
		return readAxes(null);
	}

	/**
	 * Read Axes
	 * 
	 * @param type
	 *            coordinate system type
	 * 
	 * @return axes
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<Axis> readAxes(CoordinateSystemType type) throws IOException {

		boolean isTemporalCountMeasure = type != null
				&& WKTUtils.isTemporalCountMeasure(type);

		List<Axis> axes = new ArrayList<>();

		do {

			if (!axes.isEmpty()) {
				readSeparator();
			}

			axes.add(readAxis(type));

			if (isTemporalCountMeasure) {
				break;
			}

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.AXIS));

		return axes;
	}

	/**
	 * Read an Axis
	 * 
	 * @return axis
	 * @throws IOException
	 *             upon failure to read
	 */
	public Axis readAxis() throws IOException {
		return readAxis(null);
	}

	/**
	 * Read an Axis
	 * 
	 * @param type
	 *            coordinate system type
	 * @return axis
	 * @throws IOException
	 *             upon failure to read
	 */
	public Axis readAxis(CoordinateSystemType type) throws IOException {

		Axis axis = new Axis();

		readKeyword(CoordinateReferenceSystemKeyword.AXIS);

		readLeftDelimiter();

		String nameAbbrev = reader.readExpectedToken();
		if (nameAbbrev.matches(AXIS_NAME_ABBREV_PATTERN)) {
			int abbrevIndex = nameAbbrev
					.lastIndexOf(WKTConstants.AXIS_ABBREV_LEFT_DELIMITER);
			axis.setAbbreviation(nameAbbrev.substring(abbrevIndex + 1,
					nameAbbrev.length() - 1));
			if (abbrevIndex > 0) {
				axis.setName(nameAbbrev.substring(0, abbrevIndex - 1));
			}
		} else {
			axis.setName(nameAbbrev);
		}

		readSeparator();

		String axisDirectionTypeName = reader.readToken();
		AxisDirectionType axisDirectionType = AxisDirectionType
				.getType(axisDirectionTypeName);
		if (axisDirectionType == null) {
			if (axisDirectionTypeName
					.equalsIgnoreCase(WKTConstants.AXIS_DIRECTION_OTHER)) {
				axisDirectionType = AxisDirectionType.UNSPECIFIED;
			} else {
				throw new ProjectionException(
						"Unexpected axis direction type. found: "
								+ axisDirectionTypeName);
			}
		}
		axis.setDirection(axisDirectionType);

		if (type != null) {

			switch (axisDirectionType) {

			case NORTH:
			case SOUTH:

				if (isKeywordNext(CoordinateReferenceSystemKeyword.MERIDIAN)) {

					readSeparator();
					readKeyword(CoordinateReferenceSystemKeyword.MERIDIAN);

					readLeftDelimiter();

					axis.setMeridian(reader.readNumber());

					readSeparator();

					axis.setMeridianUnit(readAngleUnit());

					readRightDelimiter();

				}

				break;

			case CLOCKWISE:
			case COUNTER_CLOCKWISE:

				readSeparator();

				readKeyword(CoordinateReferenceSystemKeyword.BEARING);

				readLeftDelimiter();

				axis.setBearing(reader.readNumber());

				readRightDelimiter();

				break;

			default:
			}

			if (isKeywordNext(CoordinateReferenceSystemKeyword.ORDER)) {

				readSeparator();
				readKeyword(CoordinateReferenceSystemKeyword.ORDER);

				readLeftDelimiter();

				axis.setOrder(reader.readUnsignedInteger());

				readRightDelimiter();

			}

			if (WKTUtils.isSpatial(type)) {

				if (isSpatialUnitNext()) {

					readSeparator();

					axis.setUnit(readUnit());

				}

			} else if (WKTUtils.isTemporalCountMeasure(type)) {

				if (isTimeUnitNext()) {

					readSeparator();

					axis.setUnit(readTimeUnit());

				}

			}

			CoordinateReferenceSystemKeyword keyword = readToKeyword(
					CoordinateReferenceSystemKeyword.ID);
			if (keyword == CoordinateReferenceSystemKeyword.ID) {
				axis.setIdentifiers(readIdentifiers());
			}

		}

		readRightDelimiter();

		return axis;
	}

	/**
	 * Read Remark
	 * 
	 * @return remark
	 * @throws IOException
	 *             upon failure to read
	 */
	public String readRemark() throws IOException {
		return readKeywordDelimitedToken(
				CoordinateReferenceSystemKeyword.REMARK);
	}

	/**
	 * Read Usages
	 * 
	 * @return usages
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<Usage> readUsages() throws IOException {

		List<Usage> usages = new ArrayList<>();

		do {

			if (!usages.isEmpty()) {
				readSeparator();
			}

			usages.add(readUsage());

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.USAGE));

		return usages;
	}

	/**
	 * Read a Usage
	 * 
	 * @return usage
	 * @throws IOException
	 *             upon failure to read
	 */
	public Usage readUsage() throws IOException {

		Usage usage = new Usage();

		readKeyword(CoordinateReferenceSystemKeyword.USAGE);

		readLeftDelimiter();

		usage.setScope(readScope());

		usage.setExtent(readExtent());

		readRightDelimiter();

		return usage;
	}

	/**
	 * Read a Scope
	 * 
	 * @return scope
	 * @throws IOException
	 *             upon failure to read
	 */
	public String readScope() throws IOException {
		return readKeywordDelimitedToken(
				CoordinateReferenceSystemKeyword.SCOPE);
	}

	/**
	 * Read an Extent
	 * 
	 * @return extent
	 * @throws IOException
	 *             upon failure to read
	 */
	public Extent readExtent() throws IOException {

		Extent extent = new Extent();

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.AREA,
				CoordinateReferenceSystemKeyword.BBOX,
				CoordinateReferenceSystemKeyword.VERTICALEXTENT,
				CoordinateReferenceSystemKeyword.TIMEEXTENT);

		if (keyword == null) {
			throw new ProjectionException("Missing extent type of ["
					+ CoordinateReferenceSystemKeyword.AREA + ", "
					+ CoordinateReferenceSystemKeyword.BBOX + ", "
					+ CoordinateReferenceSystemKeyword.VERTICALEXTENT + ", "
					+ CoordinateReferenceSystemKeyword.TIMEEXTENT + "]");
		}

		if (keyword == CoordinateReferenceSystemKeyword.AREA) {
			extent.setAreaDescription(readAreaDescription());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.BBOX,
					CoordinateReferenceSystemKeyword.VERTICALEXTENT,
					CoordinateReferenceSystemKeyword.TIMEEXTENT);
		}

		if (keyword == CoordinateReferenceSystemKeyword.BBOX) {
			extent.setGeographicBoundingBox(readGeographicBoundingBox());
			keyword = readToKeyword(
					CoordinateReferenceSystemKeyword.VERTICALEXTENT,
					CoordinateReferenceSystemKeyword.TIMEEXTENT);
		}

		if (keyword == CoordinateReferenceSystemKeyword.VERTICALEXTENT) {
			extent.setVerticalExtent(readVerticalExtent());
			keyword = readToKeyword(
					CoordinateReferenceSystemKeyword.TIMEEXTENT);
		}

		if (keyword == CoordinateReferenceSystemKeyword.TIMEEXTENT) {
			extent.setTemporalExtent(readTemporalExtent());
		}

		return extent;
	}

	/**
	 * Read an Area description
	 * 
	 * @return area description
	 * @throws IOException
	 *             upon failure to read
	 */
	public String readAreaDescription() throws IOException {
		return readKeywordDelimitedToken(CoordinateReferenceSystemKeyword.AREA);
	}

	/**
	 * Read a Geographic bounding box
	 * 
	 * @return geographic bounding box
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeographicBoundingBox readGeographicBoundingBox()
			throws IOException {

		GeographicBoundingBox boundingBox = new GeographicBoundingBox();

		readKeyword(CoordinateReferenceSystemKeyword.BBOX);

		readLeftDelimiter();

		boundingBox.setLowerLeftLatitude(reader.readNumber());

		readSeparator();

		boundingBox.setLowerLeftLongitude(reader.readNumber());

		readSeparator();

		boundingBox.setUpperRightLatitude(reader.readNumber());

		readSeparator();

		boundingBox.setUpperRightLongitude(reader.readNumber());

		readRightDelimiter();

		return boundingBox;
	}

	/**
	 * Read a Vertical extent
	 * 
	 * @return vertical extent
	 * @throws IOException
	 *             upon failure to read
	 */
	public VerticalExtent readVerticalExtent() throws IOException {

		VerticalExtent verticalExtent = new VerticalExtent();

		readKeyword(CoordinateReferenceSystemKeyword.VERTICALEXTENT);

		readLeftDelimiter();

		verticalExtent.setMinimumHeight(reader.readNumber());

		readSeparator();

		verticalExtent.setMaximumHeight(reader.readNumber());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.LENGTHUNIT);
		if (keyword == CoordinateReferenceSystemKeyword.LENGTHUNIT) {
			verticalExtent.setUnit(readLengthUnit());
		}

		readRightDelimiter();

		return verticalExtent;
	}

	/**
	 * Read a Temporal extent
	 * 
	 * @return temporal extent
	 * @throws IOException
	 *             upon failure to read
	 */
	public TemporalExtent readTemporalExtent() throws IOException {

		TemporalExtent temporalExtent = new TemporalExtent();

		readKeyword(CoordinateReferenceSystemKeyword.TIMEEXTENT);

		readLeftDelimiter();

		temporalExtent.setStart(reader.readExpectedToken());

		readSeparator();

		temporalExtent.setEnd(reader.readExpectedToken());

		readRightDelimiter();

		return temporalExtent;
	}

	/**
	 * Read a Map projection
	 * 
	 * @return map projection
	 * @throws IOException
	 *             upon failure to read
	 */
	public MapProjection readMapProjection() throws IOException {

		MapProjection mapProjection = new MapProjection();

		readKeyword(CoordinateReferenceSystemKeyword.CONVERSION);

		readLeftDelimiter();

		mapProjection.setName(reader.readExpectedToken());

		readSeparator();

		mapProjection.setMethod(readMethod());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.PARAMETER,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.PARAMETER) {
			mapProjection.setParameters(readMapProjectionParameters());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			mapProjection.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return mapProjection;
	}

	/**
	 * Read an operation method
	 * 
	 * @return operation method
	 * @throws IOException
	 *             upon failure to read
	 */
	public OperationMethod readMethod() throws IOException {

		OperationMethod method = new OperationMethod();

		readKeyword(CoordinateReferenceSystemKeyword.METHOD);

		readLeftDelimiter();

		method.setName(reader.readExpectedToken());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID);
		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			method.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return method;
	}

	/**
	 * Read Map projection parameters
	 * 
	 * @return map projection parameters
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<OperationParameter> readMapProjectionParameters()
			throws IOException {
		return readParameters(CoordinateReferenceSystemType.PROJECTED);
	}

	/**
	 * Read Operation parameters
	 * 
	 * @param type
	 *            coordinate reference system type
	 * 
	 * @return operation parameters
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<OperationParameter> readParameters(
			CoordinateReferenceSystemType type) throws IOException {

		List<OperationParameter> parameters = new ArrayList<>();

		do {

			if (!parameters.isEmpty()) {
				readSeparator();
			}

			parameters.add(readParameter(type));

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.PARAMETER));

		return parameters;
	}

	/**
	 * Read an Operation parameter
	 * 
	 * @param type
	 *            coordinate reference system type
	 * 
	 * @return operation parameter
	 * @throws IOException
	 *             upon failure to read
	 */
	public OperationParameter readParameter(CoordinateReferenceSystemType type)
			throws IOException {

		OperationParameter parameter = new OperationParameter();

		readKeyword(CoordinateReferenceSystemKeyword.PARAMETER);

		readLeftDelimiter();

		parameter.setName(reader.readExpectedToken());

		readSeparator();

		parameter.setValue(reader.readNumber());

		CoordinateReferenceSystemKeyword[] keywords = null;
		switch (type) {
		case PROJECTED:
			keywords = new CoordinateReferenceSystemKeyword[] {
					CoordinateReferenceSystemKeyword.LENGTHUNIT,
					CoordinateReferenceSystemKeyword.ANGLEUNIT,
					CoordinateReferenceSystemKeyword.SCALEUNIT,
					CoordinateReferenceSystemKeyword.ID };
			break;
		case DERIVED:
		case COORDINATE_OPERATION:
			keywords = new CoordinateReferenceSystemKeyword[] {
					CoordinateReferenceSystemKeyword.LENGTHUNIT,
					CoordinateReferenceSystemKeyword.ANGLEUNIT,
					CoordinateReferenceSystemKeyword.SCALEUNIT,
					CoordinateReferenceSystemKeyword.TIMEUNIT,
					CoordinateReferenceSystemKeyword.PARAMETRICUNIT,
					CoordinateReferenceSystemKeyword.ID };
			break;
		case BOUND:
			keywords = new CoordinateReferenceSystemKeyword[] {
					CoordinateReferenceSystemKeyword.ID };
			break;
		default:
			throw new ProjectionException("Unsupported CRS Type: " + type);
		}

		CoordinateReferenceSystemKeyword keyword = readToKeyword(keywords);

		if (keyword != null && keyword != CoordinateReferenceSystemKeyword.ID) {
			parameter.setUnit(readUnit());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			parameter.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return parameter;
	}

	/**
	 * Read a Temporal Datum
	 * 
	 * @return temporal datum
	 * @throws IOException
	 *             upon failure to read
	 */
	public TemporalDatum readTemporalDatum() throws IOException {

		TemporalDatum temporalDatum = new TemporalDatum();

		readKeyword(CoordinateReferenceSystemKeyword.TDATUM);

		readLeftDelimiter();

		temporalDatum.setName(reader.readExpectedToken());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.CALENDAR,
				CoordinateReferenceSystemKeyword.TIMEORIGIN,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.CALENDAR) {
			temporalDatum.setCalendar(readKeywordDelimitedToken(
					CoordinateReferenceSystemKeyword.CALENDAR));
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.TIMEORIGIN,
					CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.TIMEORIGIN) {
			temporalDatum.setOrigin(readKeywordDelimitedToken(
					CoordinateReferenceSystemKeyword.TIMEORIGIN));
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			temporalDatum.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return temporalDatum;
	}

	/**
	 * Read a Deriving Conversion
	 * 
	 * @return deriving conversion
	 * @throws IOException
	 *             upon failure to read
	 */
	public DerivingConversion readDerivingConversion() throws IOException {

		DerivingConversion derivingConversion = new DerivingConversion();

		readKeyword(CoordinateReferenceSystemKeyword.DERIVINGCONVERSION);

		readLeftDelimiter();

		derivingConversion.setName(reader.readExpectedToken());

		readSeparator();

		derivingConversion.setMethod(readMethod());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.PARAMETER,
				CoordinateReferenceSystemKeyword.PARAMETERFILE,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.PARAMETER
				|| keyword == CoordinateReferenceSystemKeyword.PARAMETERFILE) {
			derivingConversion.setParameters(readDerivedParameters());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			derivingConversion.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return derivingConversion;
	}

	/**
	 * Read Derived parameters
	 * 
	 * @return derived parameters
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<Parameter> readDerivedParameters() throws IOException {
		return readParametersAndFiles(CoordinateReferenceSystemType.DERIVED);
	}

	/**
	 * Read Operation parameters and parameter files
	 * 
	 * @param type
	 *            coordinate reference system type
	 * 
	 * @return operation parameters
	 * @throws IOException
	 *             upon failure to read
	 */
	public List<Parameter> readParametersAndFiles(
			CoordinateReferenceSystemType type) throws IOException {

		List<Parameter> parameters = new ArrayList<>();

		do {

			if (!parameters.isEmpty()) {
				readSeparator();
			}

			if (isKeywordNext(CoordinateReferenceSystemKeyword.PARAMETERFILE)) {
				parameters.add(readParameterFile());
			} else {
				parameters.add(readParameter(type));
			}

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.PARAMETER,
				CoordinateReferenceSystemKeyword.PARAMETERFILE));

		return parameters;
	}

	/**
	 * Read an Operation parameter file
	 * 
	 * @return operation parameter file
	 * @throws IOException
	 *             upon failure to read
	 */
	public OperationParameterFile readParameterFile() throws IOException {

		OperationParameterFile parameterFile = new OperationParameterFile();

		readKeyword(CoordinateReferenceSystemKeyword.PARAMETERFILE);

		readLeftDelimiter();

		parameterFile.setName(reader.readExpectedToken());

		readSeparator();

		parameterFile.setFileName(reader.readExpectedToken());

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			parameterFile.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		return parameterFile;
	}

	/**
	 * Read a Backward Compatible Geodetic or Geographic CRS
	 * 
	 * @return geodetic or geographic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeoCoordinateReferenceSystem readGeoCompat() throws IOException {
		CoordinateReferenceSystemType expectedType = null;
		return readGeoCompat(expectedType);
	}

	/**
	 * Read a Backward Compatible Geodetic CRS
	 * 
	 * @return geodetic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeoCoordinateReferenceSystem readGeodeticCompat()
			throws IOException {
		return readGeoCompat(CoordinateReferenceSystemType.GEODETIC);
	}

	/**
	 * Read a Backward Compatible Geographic CRS
	 * 
	 * @return geographic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeoCoordinateReferenceSystem readGeographicCompat()
			throws IOException {
		return readGeoCompat(CoordinateReferenceSystemType.GEOGRAPHIC);
	}

	/**
	 * Read a Backward Compatible Geodetic or Geographic CRS
	 * 
	 * @param expectedType
	 *            expected coordinate reference system type
	 * 
	 * @return geodetic or geographic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public GeoCoordinateReferenceSystem readGeoCompat(
			CoordinateReferenceSystemType expectedType) throws IOException {

		GeoCoordinateReferenceSystem crs = new GeoCoordinateReferenceSystem();

		CoordinateReferenceSystemKeyword type = readKeyword(
				CoordinateReferenceSystemKeyword.GEOCCS,
				CoordinateReferenceSystemKeyword.GEOGCS,
				CoordinateReferenceSystemKeyword.GEODCRS,
				CoordinateReferenceSystemKeyword.GEOGCRS);
		CoordinateReferenceSystemType crsType = WKTUtils
				.getCoordinateReferenceSystemType(type);
		if (expectedType != null && crsType != expectedType) {
			throw new ProjectionException(
					"Unexpected Coordinate Reference System Type. expected: "
							+ expectedType + ", found: " + crsType);
		}
		crs.setType(crsType);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();
		GeoReferenceFrame referenceFrame = readGeoReferenceFrame();
		referenceFrame.setType(crsType);
		crs.setReferenceFrame(referenceFrame);

		crs.setCoordinateSystem(
				readCoordinateSystemCompat(crsType, crs.getReferenceFrame()));

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.EXTENSION,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.EXTENSION) {
			extras.putAll(readExtensionsCompat());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			crs.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		crs.setRemark(writeExtras());

		return crs;
	}

	/**
	 * Read a Backward Compatible Projected CRS
	 * 
	 * @return projected coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjectedCompat()
			throws IOException {
		CoordinateReferenceSystemType expectedType = null;
		return readProjectedCompat(expectedType);
	}

	/**
	 * Read a Backward Compatible Projected Geodetic CRS
	 * 
	 * @return projected geodetic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjectedGeodeticCompat()
			throws IOException {
		return readProjectedCompat(CoordinateReferenceSystemType.GEODETIC);
	}

	/**
	 * Read a Backward Compatible Projected Geographic CRS
	 * 
	 * @return projected geographic coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjectedGeographicCompat()
			throws IOException {
		return readProjectedCompat(CoordinateReferenceSystemType.GEOGRAPHIC);
	}

	/**
	 * Read a Backward Compatible Projected CRS
	 * 
	 * @param expectedBaseType
	 *            expected base coordinate reference system type
	 * 
	 * @return projected coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public ProjectedCoordinateReferenceSystem readProjectedCompat(
			CoordinateReferenceSystemType expectedBaseType) throws IOException {

		ProjectedCoordinateReferenceSystem crs = new ProjectedCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.PROJCS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();

		crs.setBase(readGeoCompat(expectedBaseType));

		// Not spec based, but some implementations provide the unit here
		Unit unit = null;
		if (isUnitNext()) {
			readSeparator();
			unit = readUnit();
		}

		readSeparator();
		MapProjection mapProjection = readMapProjectionCompat();
		crs.setMapProjection(mapProjection);

		crs.setCoordinateSystem(readCoordinateSystemCompat(
				CoordinateReferenceSystemType.PROJECTED,
				crs.getReferenceFrame()));

		if (unit != null && !crs.getCoordinateSystem().hasUnit()) {
			crs.getCoordinateSystem().setUnit(unit);
		}

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.EXTENSION,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.EXTENSION) {
			extras.putAll(readExtensionsCompat());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			crs.setIdentifiers(readIdentifiers());
		} else if (mapProjection.hasIdentifiers()) {
			crs.setIdentifiers(mapProjection.getIdentifiers());
			mapProjection.setIdentifiers(null);
		}

		readRightDelimiter();

		crs.setRemark(writeExtras());

		return crs;
	}

	/**
	 * Read a Backward Compatible Vertical CRS
	 * 
	 * @return vertical coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public VerticalCoordinateReferenceSystem readVerticalCompat()
			throws IOException {

		VerticalCoordinateReferenceSystem crs = new VerticalCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.VERT_CS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();
		crs.setReferenceFrame(readVerticalDatumCompat());

		crs.setCoordinateSystem(readCoordinateSystemCompat(
				CoordinateReferenceSystemType.VERTICAL,
				crs.getReferenceFrame()));

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.EXTENSION,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.EXTENSION) {
			extras.putAll(readExtensionsCompat());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			crs.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		crs.setRemark(writeExtras());

		return crs;
	}

	/**
	 * Read a Backward Compatible Engineering CRS
	 * 
	 * @return engineering coordinate reference system
	 * @throws IOException
	 *             upon failure to read
	 */
	public EngineeringCoordinateReferenceSystem readEngineeringCompat()
			throws IOException {

		EngineeringCoordinateReferenceSystem crs = new EngineeringCoordinateReferenceSystem();

		readKeyword(CoordinateReferenceSystemKeyword.LOCAL_CS);

		readLeftDelimiter();

		crs.setName(reader.readExpectedToken());

		readSeparator();
		crs.setEngineeringDatum(readEngineeringDatumCompat());

		crs.setCoordinateSystem(readCoordinateSystemCompat(
				CoordinateReferenceSystemType.ENGINEERING,
				crs.getEngineeringDatum()));

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.EXTENSION,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.EXTENSION) {
			extras.putAll(readExtensionsCompat());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.ID);
		}

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			crs.setIdentifiers(readIdentifiers());
		}

		readRightDelimiter();

		crs.setRemark(writeExtras());

		return crs;
	}

	/**
	 * Read a Backward Compatible map projection
	 * 
	 * @return map projection
	 * @throws IOException
	 *             upon failure to read
	 */
	public MapProjection readMapProjectionCompat() throws IOException {

		MapProjection mapProjection = new MapProjection();

		OperationMethod method = readMethod();
		mapProjection.setName(method.getName());
		mapProjection.setMethod(method);

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.PARAMETER,
				CoordinateReferenceSystemKeyword.ID);

		if (keyword == CoordinateReferenceSystemKeyword.PARAMETER) {
			mapProjection.setParameters(readMapProjectionParameters());
		}

		if (isKeywordNext(CoordinateReferenceSystemKeyword.ID)) {
			readSeparator();
			mapProjection.setIdentifiers(readIdentifiers());
		}

		return mapProjection;
	}

	/**
	 * Read a Backward Compatible Coordinate System
	 * 
	 * @param type
	 *            coordinate reference system type
	 * @param datum
	 *            reference frame
	 * 
	 * @return coordinate system
	 * @throws IOException
	 *             upon failure to read
	 */
	public CoordinateSystem readCoordinateSystemCompat(
			CoordinateReferenceSystemType type, ReferenceFrame datum)
			throws IOException {

		CoordinateSystem coordinateSystem = new CoordinateSystem();

		switch (datum.getType()) {
		case GEODETIC:
		case GEOGRAPHIC:
			coordinateSystem.setType(CoordinateSystemType.ELLIPSOIDAL);
			break;
		case VERTICAL:
			coordinateSystem.setType(CoordinateSystemType.VERTICAL);
			break;
		case ENGINEERING:
			coordinateSystem.setType(CoordinateSystemType.CARTESIAN);
			break;
		default:
			throw new ProjectionException(
					"Unexpected Reference Frame Type. expected: "
							+ datum.getType());
		}

		if (isUnitNext()) {
			readSeparator();
			coordinateSystem.setUnit(readUnit());
		}

		if (isKeywordNext(CoordinateReferenceSystemKeyword.AXIS)) {
			readSeparator();
			coordinateSystem.setAxes(readAxes());
		} else {

			switch (type) {
			case GEOGRAPHIC:
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_LON,
						AxisDirectionType.EAST));
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_LAT,
						AxisDirectionType.NORTH));
				break;
			case PROJECTED:
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_X,
						AxisDirectionType.EAST));
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_Y,
						AxisDirectionType.NORTH));
				break;
			case GEODETIC:
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_X,
						AxisDirectionType.UNSPECIFIED));
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_Y,
						AxisDirectionType.EAST));
				coordinateSystem.addAxis(new Axis(WKTConstants.AXIS_NAME_Z,
						AxisDirectionType.NORTH));
				break;
			default:
				throw new ProjectionException(
						"Unexpected Coordinate Reference System Type: " + type);
			}

		}
		coordinateSystem.setDimension(coordinateSystem.getAxes().size());

		// TODO http://ogc.standardstracker.org/show_request.cgi?id=674
		if (isUnitNext()) {
			readSeparator();
			coordinateSystem.setUnit(readUnit());
		}

		return coordinateSystem;
	}

	/**
	 * Read a Backward Compatible vertical datum
	 * 
	 * @return vertical reference frame
	 * @throws IOException
	 *             upon failure to read
	 */
	public VerticalReferenceFrame readVerticalDatumCompat() throws IOException {
		ReferenceFrame referenceFrame = readDatumCompat();
		if (!(referenceFrame instanceof VerticalReferenceFrame)) {
			throw new ProjectionException(
					"Datum was not an expected Vertical Reference Frame");
		}
		return (VerticalReferenceFrame) referenceFrame;
	}

	/**
	 * Read a Backward Compatible engineering datum
	 * 
	 * @return engineering datum
	 * @throws IOException
	 *             upon failure to read
	 */
	public EngineeringDatum readEngineeringDatumCompat() throws IOException {
		ReferenceFrame referenceFrame = readDatumCompat();
		if (!(referenceFrame instanceof EngineeringDatum)) {
			throw new ProjectionException(
					"Datum was not an expected Engineering Datum");
		}
		return (EngineeringDatum) referenceFrame;
	}

	/**
	 * Read a Backward Compatible datum
	 * 
	 * @return reference frame
	 * @throws IOException
	 *             upon failure to read
	 */
	public ReferenceFrame readDatumCompat() throws IOException {

		ReferenceFrame referenceFrame = null;

		CoordinateReferenceSystemKeyword type = readKeyword(
				CoordinateReferenceSystemKeyword.VDATUM,
				CoordinateReferenceSystemKeyword.EDATUM);
		switch (type) {
		case VDATUM:
			referenceFrame = new VerticalReferenceFrame();
			break;
		case EDATUM:
			referenceFrame = new EngineeringDatum();
			break;
		default:
			throw new ProjectionException("Unexpected Datum type: " + type);
		}

		readLeftDelimiter();

		referenceFrame.setName(reader.readExpectedToken());

		readSeparator();
		extras.put(WKTConstants.DATUM_TYPE,
				Double.toString(reader.readNumber()));

		CoordinateReferenceSystemKeyword keyword = readToKeyword(
				CoordinateReferenceSystemKeyword.ID,
				CoordinateReferenceSystemKeyword.EXTENSION);

		if (keyword == CoordinateReferenceSystemKeyword.ID) {
			referenceFrame.setIdentifiers(readIdentifiers());
			keyword = readToKeyword(CoordinateReferenceSystemKeyword.EXTENSION);
		}

		if (keyword == CoordinateReferenceSystemKeyword.EXTENSION) {
			extras.putAll(readExtensionsCompat());
		}

		readRightDelimiter();

		return referenceFrame;
	}

	/**
	 * Read Backward Compatible Extensions
	 * 
	 * @return extensions
	 * @throws IOException
	 *             upon failure to read
	 */
	public Map<String, String> readExtensionsCompat() throws IOException {

		Map<String, String> extensions = new LinkedHashMap<>();

		do {

			if (!extensions.isEmpty()) {
				readSeparator();
			}

			readKeyword(CoordinateReferenceSystemKeyword.EXTENSION);

			readLeftDelimiter();

			String key = reader.readExpectedToken();
			readSeparator();
			String value = reader.readExpectedToken();

			extensions.put(key, value);

			readRightDelimiter();

		} while (isKeywordNext(CoordinateReferenceSystemKeyword.EXTENSION));

		return extensions;
	}

	/**
	 * Write backwards compatible extras map to text
	 * 
	 * @return extras text
	 * @throws IOException
	 *             upon failure to write
	 */
	public String writeExtras() throws IOException {
		return writeExtras(extras);
	}

	/**
	 * Write backwards compatible extras map to text
	 * 
	 * @param extras
	 *            extras map
	 * @return extras text
	 * @throws IOException
	 *             upon failure to write
	 */
	public static String writeExtras(Map<String, String> extras)
			throws IOException {

		String value = null;

		if (!extras.isEmpty()) {

			StringBuilder builder = new StringBuilder();

			for (Entry<String, String> extension : extras.entrySet()) {

				if (builder.length() > 0) {
					builder.append(WKTConstants.SEPARATOR);
				}

				builder.append(WKTConstants.LEFT_DELIMITER);
				builder.append("\"");
				builder.append(extension.getKey());
				builder.append("\"");
				builder.append(WKTConstants.SEPARATOR);
				builder.append("\"");
				builder.append(extension.getValue());
				builder.append("\"");
				builder.append(WKTConstants.RIGHT_DELIMITER);
			}

			value = builder.toString();
		}

		return value;
	}

	/**
	 * Read backwards compatible extras text (extensions, unsupported values)
	 * that were saved as CRS remarks, retrievable by
	 * {@link CoordinateReferenceSystem#getRemark()}
	 * 
	 * @param text
	 *            extras text
	 * @return extras map
	 * @throws IOException
	 *             upon failure to read
	 */
	public static Map<String, String> readExtras(String text)
			throws IOException {

		Map<String, String> extras = new LinkedHashMap<>();

		CRSReader reader = new CRSReader(text);
		try {

			while (reader.peekLeftDelimiter()) {
				reader.readLeftDelimiter();

				String key = reader.getTextReader().readExpectedToken();
				reader.readSeparator();
				String value = reader.getTextReader().readExpectedToken();
				extras.put(key, value);

				reader.readRightDelimiter();

				if (reader.peekSeparator()) {
					reader.readSeparator();
				}
			}
			reader.readEnd();

		} finally {
			reader.close();
		}

		return extras;
	}

}
