package com.denimgroup.threadfix.importer.impl.upload;

import com.denimgroup.threadfix.data.ScanCheckResultBean;
import com.denimgroup.threadfix.data.ScanImportStatus;
import com.denimgroup.threadfix.data.entities.DataFlowElement;
import com.denimgroup.threadfix.data.entities.Finding;
import com.denimgroup.threadfix.data.entities.Scan;
import com.denimgroup.threadfix.data.entities.ScannerType;
import com.denimgroup.threadfix.importer.impl.AbstractChannelImporter;
import com.denimgroup.threadfix.importer.util.HandlerWithBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.annotation.Nonnull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by mhatzenbuehler on 7/3/2014.
 */
public class PMDChannelImporter extends AbstractChannelImporter {
    public PMDChannelImporter() {
        super(ScannerType.PMD);
    }

    @Override
    @Transactional
    public Scan parseInput() {
        Scan returnScan = parseSAXInput(new PmdSAXParser());
        return returnScan;
    }

    public class PmdSAXParser extends HandlerWithBuilder {
        Map<FindingKey, String> findingMap = new HashMap<>();

        private Boolean inSecurityBug         = false;
        private Boolean getDataFlowElements   = false;

        private String currentChannelVulnCode = null;
        private String currentPath            = null;
        private String currentParameter       = null;
        private String currentSeverityCode    = null;
        private StringBuffer currentRawFinding	  = new StringBuffer();

        private List<DataFlowElement> dataFlowElements = null;
        private int dataFlowPosition;

        public void add(Finding finding) {
            if (finding != null) {
                finding.setNativeId(getNativeId(finding));
                finding.setIsStatic(true);
                saxFindingList.add(finding);
            }
        }

        public DataFlowElement getDataFlowElement (Attributes atts, int position) {
            String start = atts.getValue("beginline");
            Integer lineNum = null;

            if (start != null) {
                try {
                    lineNum = Integer.valueOf(start);
                } catch (NumberFormatException e) {
                    log.error("PMD had a non-integer value in its begin line number field");
                }
            }

            if (lineNum == null) {
                lineNum = -1;
            }

            return new DataFlowElement(null, lineNum, atts.getValue("file name"), position);
        }

        ////////////////////////////////////////////////////////////////////
        // Event handlers.
        ////////////////////////////////////////////////////////////////////

        public void startElement (String uri, String name,
                                  String qName, Attributes atts) {
            //if ("violation".equals(qName)) {
                String timestring; //do i need this?

            //} else
            if ("pmd".equals(qName) && "Security Code Guidelines".equals(atts.getValue("ruleset"))) {
                inSecurityBug = true;
                currentChannelVulnCode = atts.getValue("rule");
                currentSeverityCode = atts.getValue("priority");
                currentParameter = atts.getValue("variable");
                currentPath = atts.getValue("name");
            }

            if (inSecurityBug) {
                currentRawFinding.append(makeTag(name, qName, atts));
            }
        }

        public void endElement (String uri, String name, String qName) {
            if (inSecurityBug) {
                findingMap.put(FindingKey.PATH, currentPath);
                findingMap.put(FindingKey.PARAMETER, currentParameter);
                findingMap.put(FindingKey.VULN_CODE, currentChannelVulnCode);
                findingMap.put(FindingKey.SEVERITY_CODE, currentSeverityCode);
                findingMap.put(FindingKey.RAWFINDING, currentRawFinding.toString());

                Finding finding = constructFinding(findingMap);

                if (finding != null) {
                    finding.setDataFlowElements(dataFlowElements);
                    add(finding);
                }

                inSecurityBug = false;
                currentPath = null;
                currentParameter = null;
                currentChannelVulnCode = null;
                currentSeverityCode = null;
                dataFlowElements = null;
                dataFlowPosition = 0;
                getDataFlowElements = false;
                currentRawFinding.setLength(0);
            }
        }
    }

    @Nonnull
    @Override
    public ScanCheckResultBean checkFile() {
        return testSAXInput(new PmdSAXValidator());
    }

    public class PmdSAXValidator extends DefaultHandler {
        private boolean hasFindings = false;
        private boolean hasDate = false;
        private boolean correctFormat = false;

        private void setTestStatus() {
            if (!correctFormat) {
                testStatus = ScanImportStatus.WRONG_FORMAT_ERROR;
            } else if (hasDate) {
                testStatus = checkTestDate();
            }

            if ((testStatus == null || ScanImportStatus.SUCCESSFUL_SCAN == testStatus) && !hasFindings) {
                testStatus = ScanImportStatus.EMPTY_SCAN_ERROR;
            }
            else if (testStatus == null) {
                testStatus = ScanImportStatus.SUCCESSFUL_SCAN;
            }
        }

        public void endDocument() {
            setTestStatus();
        }

        public void startElement(String uri, String name, String qName, Attributes atts) throws SAXException {
            String dateFormat = "yyyy-MM-ddTkk:mm:ss.SSS";

            if("pmd".equals(qName)) {
                String timeString = atts.getValue("timestamp");
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
                try {
                    cal.setTime(sdf.parse(timeString));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                testDate = cal;
                hasDate = testDate != null;
                correctFormat = true;
            }

            if("violation".equals(qName)) { //&& "Security Code Guidelines".equals(atts.getValue("ruleset"))) {
                hasFindings = true;
                setTestStatus();
                throw new SAXException(FILE_CHECK_COMPLETED);
            }
        }
    }
}
