import { Node, mergeAttributes } from '@tiptap/core';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    infoPanel: {
      toggleInfoPanel: () => ReturnType;
    };
  }
}

const InfoPanel = Node.create({
  name: 'infoPanel',
  group: 'block',
  content: 'block+',

  parseHTML() {
    return [{ tag: 'div[data-type="info-panel"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-type': 'info-panel', class: 'info-panel' }), 0];
  },

  addCommands() {
    return {
      toggleInfoPanel:
        () =>
        ({ commands, editor }) => {
          if (editor.isActive('infoPanel')) {
            return commands.lift('infoPanel');
          }
          return commands.wrapIn('infoPanel');
        },
    };
  },
});

export default InfoPanel;
