/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.security.token;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.ServiceLoader;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.tools.CommandShell;
import org.apache.hadoop.util.ToolRunner;

/**
 *  DtUtilShell is a set of command line token file management operations.
 */
public class DtUtilShell extends CommandShell {
  private static final Log LOG = LogFactory.getLog(DtUtilShell.class);
  public static final String DT_USAGE = "hadoop dtutil " +
      "subcommand (help|print|get|append|cancel|remove|renew) " +
      "[-format (java|protobuf)] [-alias <alias>] filename...";

  // command line options
  private static final String HELP = "help";
  private static final String PRINT = "print";
  private static final String GET = "get";
  private static final String APPEND = "append";
  private static final String CANCEL = "cancel";
  private static final String REMOVE = "remove";
  private static final String RENEW = "renew";
  private static final String RENEWER = "-renewer";
  private static final String SERVICE = "-service";
  private static final String ALIAS = "-alias";
  private static final String FORMAT = "-format";
  private static final String FORMAT_JAVA = "java";
  private static final String FORMAT_PB = "protobuf";

  // configuration state from args, conf
  private Text alias = null;
  private Text service = null;
  private String renewer = null;
  private String format = FORMAT_PB;
  private ArrayList<File> tokenFiles = null;
  private File firstFile = null;

  private boolean matchAlias(Token<?> token) {
    return alias == null || token.getService().equals(alias);
  }

  private static Path fileToPath(File f) {
    return new Path("file:" + f.getAbsolutePath());
  }

  private void doFormattedWrite(File f, Credentials creds) throws IOException {
    if (format == null || format.equals(FORMAT_PB)) {
      creds.writeTokenStorageFile(fileToPath(f), getConf());
    } else { // if (format != null && format.equals(FORMAT_JAVA)) {
      creds.writeLegacyTokenStorageFile(f);
    }
  }

  /**
   * Parse the command line arguments and initialize subcommand.
   * @param args
   * @return 0 if the argument(s) were recognized, 1 otherwise
   * @throws Exception
   */
  @Override
  protected int init(String[] args) throws Exception {
    if (0 == args.length) {
      return 1;
    }
    tokenFiles = new ArrayList<File>();
    for (int i = 0; i < args.length; i++) {
      if (i == 0) {
        String command = args[0];
        if (command.equals(HELP)) {
          return 1;
        } else if (command.equals(PRINT)) {
          setSubCommand(new Print());
        } else if (command.equals(GET)) {
          setSubCommand(new Get(args[++i]));
        } else if (command.equals(APPEND)) {
          setSubCommand(new Append());
        } else if (command.equals(CANCEL)) {
          setSubCommand(new Remove(true));
        } else if (command.equals(REMOVE)) {
          setSubCommand(new Remove(false));
        } else if (command.equals(RENEW)) {
          setSubCommand(new Renew());
        }
      } else if (args[i].equals(ALIAS)) {
        alias = new Text(args[++i]);
      } else if (args[i].equals(SERVICE)) {
        service = new Text(args[++i]);
      } else if (args[i].equals(RENEWER)) {
        renewer = args[++i];
      } else if (args[i].equals(FORMAT)) {
        format = args[++i];
        if (!format.equals(FORMAT_JAVA) && !format.equals(FORMAT_PB)) {
          LOG.error(String.format("-format must be '%s' or '%s', not '%s'",
                    FORMAT_JAVA, FORMAT_PB, format));
          return 1;
        }
      } else {
        for (; i < args.length; i++) {
          File f = new File(args[i]);
          if (f.exists()) {
            tokenFiles.add(f);
          }
          if (firstFile == null) {
            firstFile = f;
          }
        }
        if (tokenFiles.size() == 0 && firstFile == null) {
          LOG.error(String.format("Must provide a filename to all commands."));
          return 1;
        }
      }
    }
    return 0;
  }

  @Override
  public String getCommandUsage() {
    return String.format("%n%s%n   %s%n   %s%n   %s%n   %s%n   %s%n   %s%n%n",
                  DT_USAGE, (new Print()).getUsage(), (new Get()).getUsage(),
                  (new Append()).getUsage(), (new Remove(true)).getUsage(),
                  (new Remove(false)).getUsage(), (new Renew()).getUsage());
  }

  private class Print extends SubCommand {
    public static final String PRINT_USAGE =
        "dtutil print [-alias <alias>] filename...";

    @Override
    public void execute() throws Exception {
      for (File tokenFile : tokenFiles) {
        System.out.println("File: " + tokenFile.getPath());
        Credentials creds =
            Credentials.readTokenStorageFile(tokenFile, getConf());
        boolean tokenHeader = true;
        String fmt = "%-24s %-20s %s%n";
        for (Token<?> token : creds.getAllTokens()) {
          if (matchAlias(token)) {
            if (tokenHeader) {
              System.out.printf(fmt, "Token kind", "Service", "URL Enc Token");
              System.out.println(StringUtils.repeat("-", 80));
              tokenHeader = false;
            }
            System.out.printf(fmt, token.getKind(), token.getService(),
                              token.encodeToUrlString());
          }
        }
      }
    }

    @Override
    public String getUsage() {
      return PRINT_USAGE;
    }
  }

  private class Get extends SubCommand {
    public static final String GET_USAGE = "dtutil get URL " +
        "[-service <scheme>] [-format (java|protobuf)] " +
        "[-alias <alias>] filename";
    private static final String PREFIX_HTTP = "http://";
    private static final String PREFIX_HTTPS = "https://";

    private String url = null;

    public Get() { }

    public Get(String arg) {
      url = arg;
    }

    public String toPrefix(Text serviceName) {
      return serviceName.toString() + "://";
    }

    public boolean isGenericUrl() {
      return url.startsWith(PREFIX_HTTP) || url.startsWith(PREFIX_HTTPS);
    }

    public String stripPrefix(String u) {
      return u.replaceFirst(PREFIX_HTTP, "").replaceFirst(PREFIX_HTTPS, "");
    }

    public boolean validate() {
      if (service != null && !isGenericUrl()) {
        LOG.error("Only provide -service with http/https URL.");
        return false;
      }
      if (service == null && isGenericUrl()) {
        LOG.error("Must provide -service with http/https URL.");
        return false;
      }
      return true;
    }

    public boolean matchService(DtFetcher fetcher) {
      Text sName = fetcher.getServiceName();
      return (service == null && url.startsWith(toPrefix(sName))) ||
             (service != null && service.equals(sName));
    }

    @Override
    public void execute() throws Exception {
      Token<?> token = null;
      ServiceLoader<DtFetcher> loader = ServiceLoader.load(DtFetcher.class);
      for (DtFetcher fetcher : loader) {
        if (matchService(fetcher)) {
          token = fetcher.getDelegationToken(renewer, stripPrefix(url));
        }
      }
      if (token == null) {
        LOG.error(String.format("Failed to fetch token from: %s", url));
        return;
      }
      LOG.info(String.format("Fetched token from: %s", url));
      Credentials creds = firstFile.exists() ?
          Credentials.readTokenStorageFile(firstFile, getConf()) :
          new Credentials();
      if (alias != null) {
        Token<?> aliasedToken = token.copyToken();
        aliasedToken.setService(alias);
        creds.addToken(alias, aliasedToken);
        LOG.info(String.format("Add token with service %s", alias.toString()));
      }
      creds.addToken(token.getService(), token);
      doFormattedWrite(firstFile, creds);
    }

    @Override
    public String getUsage() {
      return GET_USAGE;
    }
  }

  private class Append extends SubCommand {
    public static final String APPEND_USAGE =
        "dtutil append [-format (java|protobuf)] filename...";

    @Override
    public void execute() throws Exception {
      Credentials newCreds = new Credentials();
      File lastTokenFile = null;
      for (File tokenFile : tokenFiles) {
        lastTokenFile = tokenFile;
        Credentials creds = Credentials.readTokenStorageFile(tokenFile,
                                                             getConf());
        for (Token<?> token : creds.getAllTokens()) {
          newCreds.addToken(token.getService(), token);
        }
      }
      doFormattedWrite(lastTokenFile, newCreds);
    }

    @Override
    public String getUsage() {
      return APPEND_USAGE;
    }
  }

  private class Remove extends SubCommand {
    public static final String REMOVE_USAGE =
        "dtutil remove -alias <alias> [-format (java|protobuf)] filename...";
    public static final String CANCEL_USAGE =
        "dtutil cancel -alias <alias> [-format (java|protobuf)] filename...";
    private boolean cancel = false;

    public Remove(boolean arg) {
      cancel = arg;
    }

    @Override
    public boolean validate() {
      if (alias == null) {
        LOG.error("-alias flag is not optional for remove or cancel");
        return false;
      }
      return true;
    }

    @Override
    public void execute() throws Exception {
      for (File tokenFile : tokenFiles) {
        Credentials newCreds = new Credentials();
        Credentials creds = Credentials.readTokenStorageFile(tokenFile,
                                                             getConf());
        for (Token<?> token : creds.getAllTokens()) {
          if (matchAlias(token)) {
            if (token.isManaged() && cancel) {
              token.cancel(getConf());
              LOG.info(String.format("Canceled %s : %s",
                       token.getKind().toString(),
                       token.getService().toString()));
            }
          } else {
            newCreds.addToken(token.getService(), token);
          }
        }
        doFormattedWrite(tokenFile, newCreds);
      }
    }

    @Override
    public String getUsage() {
      if (cancel) {
        return CANCEL_USAGE;
      }
      return REMOVE_USAGE;
    }
  }

  private class Renew extends SubCommand {
    public static final String RENEW_USAGE =
        "dtutil renew -alias <alias> filename...";

    @Override
    public boolean validate() {
      if (alias == null) {
        LOG.error("-alias flag is not optional for renew");
        return false;
      }
      return true;
    }

    @Override
    public void execute() throws Exception {
      for (File tokenFile : tokenFiles) {
        Credentials creds = Credentials.readTokenStorageFile(tokenFile,
                                                             getConf());
        for (Token<?> token : creds.getAllTokens()) {
          if (token.isManaged() && matchAlias(token)) {
            long result = token.renew(getConf());
            LOG.info(String.format("Renewed %s : %s until %s",
                                   token.getKind().toString(),
                                   token.getService().toString(),
                                   (new Date(result)).toString()));
          }
        }
      }
    }

    @Override
    public String getUsage() {
      return RENEW_USAGE;
    }
  }

  public static void main(String[] args) throws Exception {
    System.exit(ToolRunner.run(new Configuration(), new DtUtilShell(), args));
  }
}
