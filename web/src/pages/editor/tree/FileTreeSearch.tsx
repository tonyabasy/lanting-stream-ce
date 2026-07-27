import { Input } from 'antd';
import { IconSearch } from '@tabler/icons-react';
import { useModel } from 'umi';

const FileTreeSearch: React.FC = () => {
  const { searchQuery, setSearchQuery } = useModel('fileTree');

  return (
    <div className="lt-filetree-search">
      <Input
        size="small"
        prefix={<IconSearch size={12} />}
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        allowClear
      />
    </div>
  );
};

export default FileTreeSearch;
