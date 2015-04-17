package com.tekante.jenkins;

import hudson.Extension;

import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.util.ListBoxModel;
import hudson.model.Job;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.log4j.lf5.LogLevel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * String based parameter that supports picking the string from two lists of values
 * presented at build time generated from data specified in job configuration
 * for this parameter and organized such that the command for the second list sees
 * the value from the first list and can change allowed values dynamically
 *
 * @author Chris Johnson
 * @see {@link ParameterDefinition}
 */

public class DynamicParameter extends ParameterDefinition {
  private static final Logger LOG = Logger.getLogger(DynamicParameter.class.getName());
  static final long serialVersionUID = 4;
  public String value = "";
  public String dynamicValue = "";
  public String valueOptions;
  public String firstBashScript;
  public String dynamicValueOptions;
  public String secondName;
  public String secondBashScript;
  public boolean cacheScriptResults;
  public Map<String, ListBoxModel> dynamicValuesCache;

  @DataBoundConstructor
  public DynamicParameter(String name, String description, String valueOptions, String firstBashScript, String dynamicValueOptions, String secondName, String secondBashScript, boolean cacheScriptResults) {
    super(name, description);
    this.secondName = secondName;
    this.valueOptions = valueOptions;
    this.dynamicValueOptions = dynamicValueOptions;
    this.firstBashScript = firstBashScript;
    this.secondBashScript = secondBashScript;
    this.cacheScriptResults = cacheScriptResults;
  }

  @Extension
  public static final class DescriptorImpl extends ParameterDescriptor {
    @Override
    public String getDisplayName() {
      return "Dynamic Parameter";
    }

    private DynamicParameter getDynamicParameter(String param) {
      String containsJobName = getCurrentDescriptorByNameUrl();
      String jobName = null;
      try {
        jobName = java.net.URLDecoder.decode(containsJobName.substring(containsJobName.lastIndexOf("/") + 1), "UTF-8");
      } catch (UnsupportedEncodingException e) {
        LOG.warning("Could not find parameter definition instance for parameter " + param + " due to encoding error in job name: " + e.getMessage());
        return null;
      }

      Job<?, ?> j = Hudson.getInstance().getItemByFullName(jobName, hudson.model.Job.class);
      if (j != null) {
        ParametersDefinitionProperty pdp = j.getProperty(hudson.model.ParametersDefinitionProperty.class);
        List<ParameterDefinition> pds = pdp.getParameterDefinitions();
        for (ParameterDefinition pd : pds) {
          if (this.isInstance(pd) && ((DynamicParameter) pd).getName().equalsIgnoreCase(param)) {
            return (DynamicParameter) pd;
          }
        }
      }
      LOG.warning("Could not find parameter definition instance for parameter " + param);
      return null;
    }

    public ListBoxModel doFillValueItems(@QueryParameter String name) {
      LOG.finer("Called with param: " + name);
      ListBoxModel m = new ListBoxModel();

      DynamicParameter dp = this.getDynamicParameter(name);
      if (dp != null) {
        if (dp.firstBashScript == null ? "" != null : !dp.firstBashScript.equals(""))
        {
            String csv = execOsScriptAndReturnFirstLineOfOutput(dp.firstBashScript);
            for (String s : csv.split(",")) {
              m.add(s);
            }
        }
        else
        {
        for (String s : dp.valueOptions.split("\\r?\\n")) {
          m.add(s);
        }
      }
        if (dp.cacheScriptResults){
            dp.dynamicValuesCache = new HashMap<String, ListBoxModel>();
        }
      }
      return m;
    }

    public ListBoxModel doFillDynamicValueItems(@QueryParameter String name, @QueryParameter String value) {
      ListBoxModel m = new ListBoxModel();

      DynamicParameter dp = this.getDynamicParameter(name);
      if (dp != null) {
            if (dp.secondBashScript == null ? "" != null : !dp.secondBashScript.equals(""))
            {
                // check the cache if we already have values for this key
                if (dp.cacheScriptResults && dp.dynamicValuesCache.containsKey(value)){
                    m = dp.dynamicValuesCache.get(value);
                    return m;
                }

                String csv = execOsScriptAndReturnFirstLineOfOutput(dp.secondBashScript + " " + value);
                for (String s : csv.split(",")) {
                    m.add(s);
                }

                // add new value to cache
                if (dp.cacheScriptResults) dp.dynamicValuesCache.put(value, m);
            }
            else
            {
        for (String s : dp.dynamicValueOptions.split("\\r?\\n")) {
          if (s.indexOf(value) == 0) {
            String[] str = s.split(":");
            m.add(str[1]);
          }
        }
      }
      }
      return m;
    }
  }

  @Override
  public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
    DynamicParameterValue value = req.bindJSON(DynamicParameterValue.class, jo);
    return value;
  }

  @Override
  public ParameterValue createValue(StaplerRequest req) {
    String[] value = req.getParameterValues(getName());
    String[] dynamicValue = req.getParameterValues(this.secondName);
    LOG.warning(getName() + ": " + value[0] + "\n");
    LOG.warning(this.secondName + ": " + dynamicValue[0] + "\n");
    return new DynamicParameterValue(getName(), value[0], this.secondName, dynamicValue[0]);
  }

  private static String execOsScriptAndReturnFirstLineOfOutput(String scriptName) {
    try {
        Process proc = Runtime.getRuntime().exec(scriptName);
        BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        proc.waitFor();

        String s;
        while ((s = in.readLine()) != null) {
            return s;
            // Logger.getLogger(ExtendedChoiceParameterDefinition.class.getName()).log(Level.INFO, null, in.readLine());
        }
        } catch (IOException ex) {
            LOG.severe(ex.getMessage());
            return ("error");
        }
        catch (InterruptedException ex) {
           LOG.severe(ex.getMessage());
           return ("error");
        }
        return null;
  }
}