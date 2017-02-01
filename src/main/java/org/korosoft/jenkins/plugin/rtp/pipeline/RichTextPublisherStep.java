package org.korosoft.jenkins.plugin.rtp.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
//import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.korosoft.jenkins.plugin.rtp.MarkupParser;
import org.korosoft.jenkins.plugin.rtp.Messages;
import org.korosoft.jenkins.plugin.rtp.RichTextPublisher;
import org.korosoft.jenkins.plugin.rtp.RichTextPublisher.DescriptorImpl;


public class RichTextPublisherStep extends AbstractStepImpl {
	
	private static final Log log = LogFactory.getLog(RichTextPublisherStep.class);
	
	private static final transient Pattern FILE_VAR_PATTERN = Pattern.compile("\\$\\{(file|file_sl):([^\\}]+)\\}", Pattern.CASE_INSENSITIVE);
	
	private String stableText;
	private String unstableText;
	private String failedText;
	private String parserName;
	private boolean unstableAsStable;
	private boolean failedAsStable;
	
	private transient MarkupParser markupParser;
	
	RichTextPublisherStepExecution test = new RichTextPublisherStepExecution();
	
	@DataBoundConstructor
	public RichTextPublisherStep() {
	}
	
	public String getStableText() {
	    return stableText;
	}
	@DataBoundSetter 
	public void setStableText(String stableText) {
		this.stableText = stableText==null? null:stableText;
	}
	
	public String getUnstableText() {
        return unstableText;
    }
	@DataBoundSetter
    public void setUnstableText(String unstableText) {
		this.unstableText = unstableText==null? null:unstableText;
    }
	
	public String getFailedText() {
	    return failedText;
	}
	@DataBoundSetter 
	public void setFailedText(String failedText) {
		this.failedText = failedText==null? null:failedText;
	}
	
	public boolean getIsUnstableAsStable() {
        return unstableAsStable;
    }
    @DataBoundSetter
    public void setIsUnstableAsStable(boolean unstableAsStable) {
        this.unstableAsStable = unstableAsStable;
    }

    public boolean getIsFailedAsStable() {
        return failedAsStable;
    }
    @DataBoundSetter
    public void setIsFailedAsStable(boolean failedAsStable) {
        this.failedAsStable = failedAsStable;
    }
    
    public String getParserName() {
        return parserName;
    }
    @DataBoundSetter
    public void setParserName(String parserName) {
        if (parserName == null) {
            parserName = "HTML";
        }
        this.parserName = parserName;
        this.markupParser = DescriptorImpl.markupParsers.get(parserName);
    }
    
    public MarkupParser getMarkupParser() {
        if (markupParser == null) {
            markupParser = DescriptorImpl.markupParsers.get(parserName);
        }
        return markupParser;
    }
 
    
	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

		private static transient Map<String, MarkupParser> markupParsers;
	    private static transient List<String> markupParserNames;
		
	    static {
            loadParsers();
        }
	    
        public DescriptorImpl() {
            super(RichTextPublisherStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "rtp";
        }

        @Override
        public String getDisplayName() {
            return "Publish rich text message";
        }
        
        public HttpResponse doFillParserNameItems() {
            loadParsers();
            ListBoxModel model = new ListBoxModel();
            for (String name : markupParserNames) {
                model.add(name, name);
            }
            return model;
        }
        
        private static void loadParsers() {
            Properties properties = new Properties();
            InputStream stream = DescriptorImpl.class.getResourceAsStream("/parsers.properties");
            try {
                properties.load(stream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(stream);
            }

            markupParsers = new HashMap<String, MarkupParser>();
            markupParserNames = new ArrayList<String>();
            for (Object o : properties.values()) {
                try {
                    MarkupParser parser = (MarkupParser) Class.forName(o.toString()).newInstance();
                    String name = parser.getName();
                    markupParserNames.add(name);
                    markupParsers.put(name, parser);
                } catch (Exception e) {
                    log.error(e);
                }
            }
        }
        
        public FormValidation doCheckPublishText(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value) throws IOException, ServletException {
            try {
                FilePath workspace = project.getSomeWorkspace();
                if (workspace == null) {
                    return FormValidation.warning(Messages.neverBuilt());
                }
                Matcher matcher = FILE_VAR_PATTERN.matcher(value);
                int start = 0;
                List<String> missingFiles = new ArrayList<String>();
                while (matcher.find(start)) {
                    String fileName = matcher.group(2);
                    FilePath filePath = new FilePath(workspace, fileName);
                    if (!filePath.exists()) {
                        missingFiles.add(fileName);
                    }
                    start = matcher.end();
                }
                if (missingFiles.isEmpty()) {
                    return FormValidation.ok();
                }
                if (missingFiles.size() == 1) {
                    return FormValidation.warning(Messages.fileNotFound(), missingFiles.get(0));
                }
                return FormValidation.warning(Messages.filesNotFound(), StringUtils.join(missingFiles, ", "));
            } catch (InterruptedException e) {
                return FormValidation.error(e, Messages.interrupted());
            }
        }
    }
}