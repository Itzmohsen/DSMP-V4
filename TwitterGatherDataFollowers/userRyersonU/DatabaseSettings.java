package TwitterGatherDataFollowers.userRyersonU;

/**
 * Optional MySQL connection settings. Defaults preserve the legacy local
 * database location while keeping usernames and passwords out of source.
 */
final class DatabaseSettings
{
	private DatabaseSettings()
	{
	}

	static String host()
	{
		return value("dsmp.db.host", "DSMP_DB_HOST", "127.0.0.1");
	}

	static String port()
	{
		return value("dsmp.db.port", "DSMP_DB_PORT", "3306");
	}

	static String database()
	{
		return value("dsmp.db.name", "DSMP_DB_NAME", "testmysql");
	}

	static String user()
	{
		return value("dsmp.db.user", "DSMP_DB_USER", "root");
	}

	static String password()
	{
		return value("dsmp.db.password", "DSMP_DB_PASSWORD", "");
	}

	private static String value(
			String propertyName,
			String environmentName,
			String fallback)
	{
		String configured = System.getProperty(propertyName);
		if (configured == null || configured.trim().length() == 0)
		{
			configured = System.getenv(environmentName);
		}
		return configured == null || configured.trim().length() == 0
				? fallback : configured.trim();
	}
}
