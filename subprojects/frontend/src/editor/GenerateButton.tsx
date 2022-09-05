import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import Button from '@mui/material/Button';
import { observer } from 'mobx-react-lite';
import React from 'react';

import type EditorStore from './EditorStore';

const GENERATE_LABEL = 'Generate';

const GenerateButton = observer(function GenerateButton({
  editorStore,
  hideWarnings,
}: {
  editorStore: EditorStore | undefined;
  hideWarnings?: boolean | undefined;
}): JSX.Element {
  if (editorStore === undefined) {
    return (
      <Button color="inherit" className="rounded" disabled>
        Loading&hellip;
      </Button>
    );
  }

  const { errorCount, warningCount } = editorStore;

  const diagnostics: string[] = [];
  if (errorCount > 0) {
    diagnostics.push(`${errorCount} error${errorCount === 1 ? '' : 's'}`);
  }
  if (!(hideWarnings ?? false) && warningCount > 0) {
    diagnostics.push(`${warningCount} warning${warningCount === 1 ? '' : 's'}`);
  }
  const summary = diagnostics.join(' and ');

  if (errorCount > 0) {
    return (
      <Button
        aria-label={`Select next diagnostic out of ${summary}`}
        onClick={() => editorStore.nextDiagnostic()}
        color="error"
        className="rounded"
      >
        {summary}
      </Button>
    );
  }

  return (
    <Button
      disabled={!editorStore.opened}
      color={warningCount > 0 ? 'warning' : 'primary'}
      className="rounded"
      startIcon={<PlayArrowIcon />}
    >
      {summary === '' ? GENERATE_LABEL : `${GENERATE_LABEL} (${summary})`}
    </Button>
  );
});

GenerateButton.defaultProps = {
  hideWarnings: false,
};

export default GenerateButton;
